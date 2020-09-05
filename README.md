## RedisBloomFilter

> 基于 Redis 的布隆过滤器，适用于分布式环境，主要是为了解决缓存穿透的问题

### application.properties 配置
bloom.filter.fpp = 0.01F # 容错率配置 (0.0 ~ 1.0)  
bloom.filter.filter-name = bloom:filter # 过滤器名称，对应 RedisKey  
bloom.filter.expected-insertions = 10000 # 预计容量大小

### 使用
配置预计容量大小  
bloom.filter.expected-insertions = 10000 
> 生成环境下，必须配置

@Autowired  
private RedisBloomFilter<Integer> redisBloomFilter;

### 使用建议
- 由于布隆过滤器是无法删除指定的 key 的，所以当数据库发生变化的时候，建议做一个定时器定时进行维护  
- 可以使用 reload 方法进行重置布隆过滤器


### API
- public boolean exist(T key)：验证 key 是否存在
- public void put(T... keys)：添加 key 到 bloom 过滤器
- public void delete()：删除 bloom 过滤器
- public void reload(List<T> keys)：重新加载 bloom 过滤器
- public long count()：返回布隆过滤器中元素数量
- public Long bitCount()：返回布隆过滤器中 1 值数量

