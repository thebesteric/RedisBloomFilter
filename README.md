# RedisBloomFilter

## application.properties 配置
bloom.filter.fpp = 0.01F # 容错率配置 (0.0 ~ 1.0)  
bloom.filter.filter-name = bloom:filter # 过滤器名称，对应 RedisKey  
bloom.filter.expected-insertions = 100 # 预计容量大小

## 使用
配置预计容量大小（必须）
bloom.filter.expected-insertions = 100

@Autowired  
private RedisBloomFilter<Integer> redisBloomFilter;

## API
- public boolean exist(T key)：验证 key 是否存在
- public void put(T key)：添加 key 到 bloom 过滤器
- public Boolean delete()：删除 bloom 过滤器
- public void reload(List<T> keys)：重新加载 bloom 过滤器

