package org.dromara.system.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.core.constant.RegexConstants;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.system.domain.SysDictType;

/**
 * 字典类型业务对象 sys_dict_type
 *
 * @author Michelle.Chung
 */

@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SysDictType.class, reverseConvertGenerate = false)
public class SysDictTypeBo extends BaseEntity {

    /**
     * 字典主键
     */
    @NotNull(message = "字典主键不能为空", groups = {EditGroup.class})
    private Long dictId;

    /**
     * 字典名称
     */
    @NotBlank(message = "字典名称不能为空", groups = {AddGroup.class, EditGroup.class})
    @Size(min = 0, max = 100, message = "字典类型名称长度不能超过{max}个字符", groups = {AddGroup.class, EditGroup.class})
    private String dictName;

    /**
     * 字典类型
     */
    @NotBlank(message = "字典类型不能为空", groups = {AddGroup.class, EditGroup.class})
    @Size(min = 0, max = 100, message = "字典类型类型长度不能超过{max}个字符", groups = {AddGroup.class, EditGroup.class})
    @Pattern(regexp = RegexConstants.DICTIONARY_TYPE, message = "字典类型必须以字母开头，且只能为（小写字母，数字，下滑线）", groups = {AddGroup.class, EditGroup.class})
    private String dictType;

    /**
     * 备注
     */
    private String remark;


}
