package org.wesoft.plugins.redis.filter.bloom;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "bloom.filter")
@Component
@Data
public class BloomFilterProperties {

    /** 过滤器名称 */
    private String filterName = "bloom:filter";

    /** 容错率 */
    private float fpp = 0.01F;

    /** 预加载期望值 */
    private long expectedInsertions = 1000L;

}
