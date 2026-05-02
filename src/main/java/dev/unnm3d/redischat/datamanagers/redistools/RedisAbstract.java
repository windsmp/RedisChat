package dev.unnm3d.redischat.datamanagers.redistools;

import io.lettuce.core.RedisClient;
import io.lettuce.core.TransactionResult;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.pubsub.RedisPubSubListener;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import org.bukkit.Bukkit;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;


public abstract class RedisAbstract {
    private final RoundRobinConnectionPool<String, String> roundRobinConnectionPool;
    private final ConcurrentHashMap<String[], StatefulRedisPubSubConnection<String, String>> pubSubConnections;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    protected RedisClient lettuceRedisClient;

    public RedisAbstract(RedisClient lettuceRedisClient, int poolSize) {
        this.lettuceRedisClient = lettuceRedisClient;
        this.roundRobinConnectionPool = new RoundRobinConnectionPool<>(lettuceRedisClient::connect, poolSize);
        this.pubSubConnections = new ConcurrentHashMap<>();
    }

    public abstract void receiveMessage(String channel, String message);

    protected void registerSub(String... listenedChannels) {
        if (listenedChannels.length == 0) {
            return;
        }
        final StatefulRedisPubSubConnection<String, String> pubSubConnection = lettuceRedisClient.connectPubSub();
        pubSubConnections.put(listenedChannels, pubSubConnection);
        pubSubConnection.addListener(new RedisPubSubListener<>() {
            @Override
            public void message(String channel, String message) {
                if (closed.get()) {
                    return;
                }
                receiveMessage(channel, message);
            }

            @Override
            public void message(String pattern, String channel, String message) {
            }

            @Override
            public void subscribed(String channel, long count) {
            }

            @Override
            public void psubscribed(String pattern, long count) {
            }

            @Override
            public void unsubscribed(String channel, long count) {
            }

            @Override
            public void punsubscribed(String pattern, long count) {
            }
        });
        pubSubConnection.async().subscribe(listenedChannels)
                .exceptionally(throwable -> {
                    throwable.printStackTrace();
                    return null;
                });
    }

    public <T> CompletionStage<T> getConnectionAsync(Function<RedisAsyncCommands<String, String>, CompletionStage<T>> redisCallBack) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Redis client is closed"));
        }
        try {
            return redisCallBack.apply(roundRobinConnectionPool.get().async());
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
    }

    public <T> CompletionStage<T> getConnectionPipeline(Function<RedisAsyncCommands<String, String>, CompletionStage<T>> redisCallBack) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Redis client is closed"));
        }
        try {
            StatefulRedisConnection<String, String> connection = roundRobinConnectionPool.get();
            connection.setAutoFlushCommands(false);
            CompletionStage<T> completionStage = redisCallBack.apply(connection.async());
            connection.flushCommands();
            connection.setAutoFlushCommands(true);
            return completionStage;
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
    }

    public Optional<List<Object>> executeTransaction(Consumer<RedisCommands<String, String>> redisCommandsConsumer) {
        if (closed.get()) {
            return Optional.empty();
        }
        try {
            final RedisCommands<String, String> syncCommands = roundRobinConnectionPool.get().sync();
            syncCommands.multi();
            redisCommandsConsumer.accept(syncCommands);
            final TransactionResult transactionResult = syncCommands.exec();
            return Optional.ofNullable(transactionResult.wasDiscarded() ? null : transactionResult.stream().toList());
        } catch (Throwable throwable) {
            return Optional.empty();
        }
    }

    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        pubSubConnections.values().forEach(connection -> {
            try {
                connection.async().unsubscribe().toCompletableFuture().orTimeout(1, TimeUnit.SECONDS).join();
            } catch (Throwable ignored) {
            }

            try {
                connection.close();
            } catch (Throwable ignored) {
            }
        });
        pubSubConnections.clear();
        Bukkit.getLogger().info("Closing pubsub connection");

        roundRobinConnectionPool.close();
        Bukkit.getLogger().info("Closing pooled redis connections");

        try {
            lettuceRedisClient.shutdown(0, 2, TimeUnit.SECONDS);
            Bukkit.getLogger().info("Lettuce shutdown connection");
        } catch (Throwable throwable) {
            Bukkit.getLogger().warning("Error while shutting down Lettuce: " + throwable.getMessage());
        } finally {
            lettuceRedisClient = null;
        }
    }


}
