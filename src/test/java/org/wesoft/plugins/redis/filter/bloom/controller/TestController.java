package org.wesoft.plugins.redis.filter.bloom.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.wesoft.plugins.redis.filter.bloom.RedisBloomFilter;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/test")
public class TestController {

    @Autowired
    private RedisBloomFilter<Integer> redisBloomFilter;


    @PostConstruct
    private void init() {
        for (int i = 0; i < 10; i++) {
            redisBloomFilter.put(i);
        }
    }

    @GetMapping("/get")
    public Object get(int key) {
        return redisBloomFilter.exist(key);
    }

    @GetMapping("/count")
    public Object count() {
        return redisBloomFilter.count();
    }

    @GetMapping("/reload")
    public Object reload() {
        List<Integer> keys = new ArrayList<>();
        for (int i = 10; i < 20; i++) {
            keys.add(i);
        }
        redisBloomFilter.reload(keys);
        return true;
    }

}
