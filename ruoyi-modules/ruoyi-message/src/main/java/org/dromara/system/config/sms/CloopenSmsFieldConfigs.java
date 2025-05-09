package org.dromara.system.config.sms;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.system.config.FieldConfig;
import org.dromara.system.config.SmsFieldConfigs;
import org.dromara.system.config.TemplateMode;

/**
 * 容联云短信
 *
 * @author hexm
 * @date 2025/3/9
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CloopenSmsFieldConfigs extends SmsFieldConfigs {
    /** 应用 ID */
    private FieldConfig<String> sdkAppId;
    /** REST API Base URL */
    private FieldConfig<String> baseUrl;

    public CloopenSmsFieldConfigs() {
        this.accessKeyId = FieldConfig.<String>builder()
            .component("input")
            .name("accessKeyId")
            .help("访问键标识")
            .required(true)
            .build();
        this.accessKeySecret = FieldConfig.<String>builder()
            .component("input")
            .name("accessKeySecret")
            .help("访问键秘钥")
            .required(true)
            .type("password")
            .build();
        this.sdkAppId = FieldConfig.<String>builder()
            .component("input")
            .name("应用 ID")
            .required(true)
            .build();
        this.baseUrl = FieldConfig.<String>builder()
            .value("https://app.cloopen.com:8883/2013-12-26")
            .component("input")
            .name("BaseURL")
            .required(true)
            .build();
    }

    /** 获取模板模式 */
    @Override
    public TemplateMode getTemplateMode() {
        return new TemplateMode(true, false);
    }
}
