package org.wesoft.plugins.redis.filter.bloom;

import com.google.common.hash.Funnels;
import com.google.common.hash.Hashing;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;


@Component
@SuppressWarnings("unchecked")
public class RedisBloomFilter<T> {

    @Autowired
    private BloomFilterProperties bloomFilterProperties;

    @Autowired
    private RedisTemplate redisTemplate;

    /** 预计数位 */
    long numBits;

    /** 哈希函数数量 */
    int numHashFunctions;

    @PostConstruct
    private void init() {
        float fpp = bloomFilterProperties.getFpp();
        int expectedInsertions = bloomFilterProperties.getExpectedInsertions();
        checkArgument(expectedInsertions >= 0, "Expected insertions (%s) must be >= 0", expectedInsertions);
        checkArgument(fpp > 0.0, "False positive probability (%s) must be > 0.0", fpp);
        checkArgument(fpp < 1.0, "False positive probability (%s) must be < 1.0", fpp);
        this.numBits = optimalNumOfBits(expectedInsertions, fpp);
        this.numHashFunctions = optimalNumOfHashFunctions(expectedInsertions, numBits);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
    }

    public void reload(List<T> keys) {
        new Thread(() -> {
            if (this.delete()) {
                for (T key : keys) {
                    this.put(key);
                }
            }
        }).start();
    }

    public Boolean delete() {
        return redisTemplate.delete(bloomFilterProperties.getFilterName());
    }

    public boolean exist(T key) {
        long[] indexes = getIndexes(key);
        List<Boolean> list = redisTemplate.executePipelined((RedisCallback<Object>) redisConnection -> {
            redisConnection.openPipeline();
            for (long index : indexes) {
                redisConnection.getBit(bloomFilterProperties.getFilterName().getBytes(), index);
            }
            redisConnection.close();
            return null;
        });
        return !list.contains(false);
    }

    public void put(T key) {
        long[] indexes = getIndexes(key);
        redisTemplate.executePipelined((RedisCallback<Object>) redisConnection -> {
            redisConnection.openPipeline();
            for (long index : indexes) {
                redisConnection.setBit(bloomFilterProperties.getFilterName().getBytes(), index, true);
            }
            redisConnection.close();
            return null;
        });
    }

    private long[] getIndexes(T key) {
        long hash1 = hash(key);
        long hash2 = hash1 >>> 16;
        long[] result = new long[numHashFunctions];
        for (int i = 0; i < numHashFunctions; i++) {
            long combinedHash = hash1 + i * hash2;
            if (combinedHash < 0) {
                combinedHash = ~combinedHash;
            }
            result[i] = combinedHash % numBits;
        }
        return result;
    }

    private long hash(T key) {
        Charset charset = StandardCharsets.UTF_8;
        return Hashing.murmur3_128().hashObject(key.toString(), Funnels.stringFunnel(charset)).asLong();
    }

    static long optimalNumOfBits(long n, double p) {
        if (p == 0) {
            p = Double.MIN_VALUE;
        }
        return (long) (-n * Math.log(p) / (Math.log(2) * Math.log(2)));
    }

    static int optimalNumOfHashFunctions(long n, long m) {
        // (m / n) * log(2), but avoid truncation due to division!
        return Math.max(1, (int) Math.round((double) m / n * Math.log(2)));
    }
}
