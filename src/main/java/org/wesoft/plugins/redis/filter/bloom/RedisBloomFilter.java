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
    private long numBits;

    /** 哈希函数数量 */
    private int numHashFunctions;

    /** Bloom 过滤器中元素数量 */
    private long count;

    @PostConstruct
    private void postConstruct() {
        init(null, null);
    }

    private void init(Long expectedInsertions, Float fpp) {
        fpp = fpp == null ? bloomFilterProperties.getFpp() : fpp;
        expectedInsertions = expectedInsertions == null ? bloomFilterProperties.getExpectedInsertions() : expectedInsertions;
        checkArgument(expectedInsertions >= 0, "Expected insertions (%s) must be >= 0", expectedInsertions);
        checkArgument(fpp > 0.0, "False positive probability (%s) must be > 0.0", fpp);
        checkArgument(fpp < 1.0, "False positive probability (%s) must be < 1.0", fpp);
        this.numBits = optimalNumOfBits(expectedInsertions, fpp);
        this.numHashFunctions = optimalNumOfHashFunctions(expectedInsertions, numBits);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
    }

    /**
     * 重新加载布隆过滤器
     *
     * @param keys   要添加的 keys
     * @param delete 是否删除原有过滤器
     */
    public void reload(List<T> keys, boolean... delete) {
        new Thread(() -> {
            if (delete != null && delete.length > 0) {
                if (delete[0]) {
                    this.delete();
                    init((long) keys.size(), 0.01f);
                }
            }
            for (T key : keys) {
                this.put(key);
            }
        }).start();
    }

    /**
     * 删除 Bloom 过滤器
     */
    public void delete() {
        redisTemplate.delete(bloomFilterProperties.getFilterName());
    }

    /**
     * key 是否存在
     *
     * @param key key
     */
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

    /**
     * 添加
     *
     * @param key key
     */
    public void put(T key) {
        long[] indexes = getIndexes(key);
        redisTemplate.executePipelined((RedisCallback<Object>) redisConnection -> {
            redisConnection.openPipeline();
            for (long index : indexes) {
                redisConnection.setBit(bloomFilterProperties.getFilterName().getBytes(), index, true);
            }
            count++;
            redisConnection.close();
            return null;
        });
    }

    /**
     * Bloom 过滤器中元素的数量
     */
    public long count() {
        return count;
    }

    /**
     * Bloom 过滤器中位为 true 的数量
     */
    public Long bitCount() {
        Object object = redisTemplate.execute((RedisCallback<Long>) redisConnection -> {
            Long result = redisConnection.bitCount(bloomFilterProperties.getFilterName().getBytes());
            redisConnection.close();
            return result;
        });
        return object == null ? 0L : (Long) object;
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
