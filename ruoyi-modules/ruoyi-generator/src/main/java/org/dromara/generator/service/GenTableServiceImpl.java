package org.dromara.generator.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.dromara.common.core.constant.Constants;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.DateUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.core.utils.file.FileUtils;
import org.dromara.common.core.utils.funtion.BiOperator;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.generator.constant.GenConstants;
import org.dromara.generator.domain.GenTable;
import org.dromara.generator.domain.GenTableColumn;
import org.dromara.generator.mapper.GenTableColumnMapper;
import org.dromara.generator.mapper.GenTableMapper;
import org.dromara.generator.util.GenUtils;
import org.dromara.generator.util.VelocityInitializer;
import org.dromara.generator.util.VelocityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 业务 服务层实现
 *
 * @author Lion Li
 */
@DS("#header.datasource")
@Slf4j
@Service
public class GenTableServiceImpl implements IGenTableService {

    @Autowired
    private GenTableMapper baseMapper;
    @Autowired
    private GenTableColumnMapper genTableColumnMapper;
    @Autowired
    private IdentifierGenerator identifierGenerator;

    /**
     * 查询业务字段列表
     *
     * @param tableId 业务字段编号
     * @return 业务字段集合
     */
    @Override
    public List<GenTableColumn> selectGenTableColumnListByTableId(Long tableId) {
        return genTableColumnMapper.selectList(new LambdaQueryWrapper<GenTableColumn>()
                                                   .eq(GenTableColumn::getTableId, tableId)
                                                   .orderByAsc(GenTableColumn::getSort));
    }

    /**
     * 查询业务信息
     *
     * @param id 业务ID
     * @return 业务信息
     */
    @Override
    public GenTable selectGenTableById(Long id) {
        GenTable genTable = baseMapper.selectGenTableById(id);
        setTableFromOptions(genTable);
        return genTable;
    }

    @Override
    public TableDataInfo<GenTable> selectPageGenTableList(GenTable genTable, PageQuery pageQuery) {
        Page<GenTable> page = baseMapper.selectPage(pageQuery.build(), this.buildGenTableQueryWrapper(genTable));
        return TableDataInfo.build(page);
    }

    private QueryWrapper<GenTable> buildGenTableQueryWrapper(GenTable genTable) {
        Map<String, Object> params = genTable.getParams();
        QueryWrapper<GenTable> wrapper = Wrappers.query();
        wrapper.like(StringUtils.isNotBlank(genTable.getTableName()), "lower(table_name)", StringUtils.lowerCase(genTable.getTableName()))
            .like(StringUtils.isNotBlank(genTable.getTableComment()), "lower(table_comment)", StringUtils.lowerCase(genTable.getTableComment()))
            .between(params.get("beginTime") != null && params.get("endTime") != null,
                     "create_time", params.get("beginTime"), params.get("endTime"))
            .orderByDesc("table_id");
        return wrapper;
    }


    @Override
    public TableDataInfo<GenTable> selectPageDbTableList(GenTable genTable, PageQuery pageQuery) {
        Page<GenTable> page = baseMapper.selectPageDbTableList(pageQuery.build(), genTable);
        return TableDataInfo.build(page);
    }

    /**
     * 查询据库列表
     *
     * @param tableNames 表名称组
     * @return 数据库表集合
     */
    @Override
    public List<GenTable> selectDbTableListByNames(String[] tableNames) {
        return baseMapper.selectDbTableListByNames(tableNames);
    }

    /**
     * 查询所有表信息
     *
     * @return 表信息集合
     */
    @Override
    public List<GenTable> selectGenTableAll() {
        return baseMapper.selectGenTableAll();
    }

    /**
     * 修改业务
     *
     * @param genTable 业务信息
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateGenTable(GenTable genTable) {
        String options = JsonUtils.toJsonString(genTable.getParams());
        genTable.setOptions(options);
        int row = baseMapper.updateById(genTable);
        if (row > 0) {
            for (GenTableColumn cenTableColumn : genTable.getColumns()) {
                genTableColumnMapper.updateById(cenTableColumn);
            }
        }
    }

    /**
     * 删除业务对象
     *
     * @param tableIds 需要删除的数据ID
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteGenTableByIds(Long[] tableIds) {
        List<Long> ids = Arrays.asList(tableIds);
        baseMapper.deleteBatchIds(ids);
        genTableColumnMapper.delete(new LambdaQueryWrapper<GenTableColumn>().in(GenTableColumn::getTableId, ids));
    }

    /**
     * 导入表结构
     *
     * @param tableList 导入表列表
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void importGenTable(List<GenTable> tableList) {
        String operName = LoginHelper.getUsername();
        try {
            for (GenTable table : tableList) {
                String tableName = table.getTableName();
                GenUtils.initTable(table, operName);
                int row = baseMapper.insert(table);
                if (row > 0) {
                    // 保存列信息
                    List<GenTableColumn> genTableColumns = genTableColumnMapper.selectDbTableColumnsByName(tableName);
                    List<GenTableColumn> saveColumns = new ArrayList<>();
                    for (GenTableColumn column : genTableColumns) {
                        GenUtils.initColumnField(column, table);
                        saveColumns.add(column);
                    }
                    if (CollUtil.isNotEmpty(saveColumns)) {
                        genTableColumnMapper.insertBatch(saveColumns);
                    }
                }
            }
        } catch (Exception e) {
            throw new ServiceException("导入失败：" + e.getMessage());
        }
    }

    /**
     * 预览代码
     *
     * @param tableId 表编号
     * @return 预览数据列表
     */
    @Override
    public Map<String, String> previewCode(Long tableId) {
        Map<String, String> dataMap = new LinkedHashMap<>();
        // 查询表信息
        GenTable table = baseMapper.selectGenTableById(tableId);
        List<Long> menuIds = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            menuIds.add(identifierGenerator.nextId(null).longValue());
        }
        table.setMenuIds(menuIds);
        // 设置主键列信息
        setPkColumn(table);
        // 设置其他选项
        setTableFromOptions(table);
        VelocityInitializer.initVelocity();

        VelocityContext context = VelocityUtils.prepareContext(table);

        // 获取模板列表
        List<String> templates = VelocityUtils.getTemplateList(table);
        for (String template : templates) {
            // 渲染模板
            StringWriter sw = new StringWriter();
            Template tpl = Velocity.getTemplate(template, Constants.UTF8);
            tpl.merge(context, sw);
            dataMap.put(template, sw.toString());
        }
        return dataMap;
    }

    /**
     * 生成代码（下载方式）
     *
     * @param tableName 表名称
     * @return 数据
     */
    @Override
    public byte[] downloadCode(String tableName) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(outputStream);
        generatorCode(tableName, zip);
        IoUtil.close(zip);
        return outputStream.toByteArray();
    }

    /**
     * 生成代码（自定义路径）
     *
     * @param tableName 表名称
     */
    @Override
    public void generatorCode(String tableName) {
        // 查询表信息
        GenTable table = baseMapper.selectGenTableByName(tableName);
        // 设置主键列信息
        setPkColumn(table);
        // 设置其他选项
        setTableFromOptions(table);

        VelocityInitializer.initVelocity();

        VelocityContext context = VelocityUtils.prepareContext(table);

        // 获取模板列表
        List<String> templates = VelocityUtils.getTemplateList(table);
        for (String template : templates) {
            if (!StringUtils.containsAny(template, "sql.vm", "api.js.vm", "index.vue.vm", "index-tree.vue.vm")) {
                // 渲染模板
                StringWriter sw = new StringWriter();
                Template tpl = Velocity.getTemplate(template, Constants.UTF8);
                tpl.merge(context, sw);
                try {
                    String path = getGenPath(table, template);
                    FileUtils.writeUtf8String(sw.toString(), path);
                } catch (Exception e) {
                    throw new ServiceException("渲染模板失败，表名：" + table.getTableName());
                }
            }
        }
    }

    /**
     * 同步数据库
     *
     * @param tableName 表名称
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void synchDb(String tableName) {
        GenTable table = baseMapper.selectGenTableByName(tableName);
        List<GenTableColumn> tableColumns = table.getColumns();
        Map<String, GenTableColumn> tableColumnMap = tableColumns.stream()
            .collect(Collectors.toMap(GenTableColumn::getColumnName, Function.identity(), BiOperator::last));

        List<GenTableColumn> dbTableColumns = genTableColumnMapper.selectDbTableColumnsByName(tableName);
        if (CollUtil.isEmpty(dbTableColumns)) {
            throw new ServiceException("同步数据失败，原表结构不存在");
        }
        List<String> dbTableColumnNames = dbTableColumns.stream().map(GenTableColumn::getColumnName).collect(Collectors.toList());

        // 本地中不存在的字段，待增加
        List<GenTableColumn> saveColumns = new ArrayList<>();
        dbTableColumns.forEach(column -> {
            if (tableColumnMap.containsKey(column.getColumnName())) {
                // 更新
                GenTableColumn prevColumn = tableColumnMap.get(column.getColumnName());
                prevColumn.setColumnType(column.getColumnType());
                prevColumn.setIsPk(column.getIsPk());
                prevColumn.setIsIncrement(column.getIsIncrement());
                prevColumn.setSort(column.getSort());
                prevColumn.setUpdateTime(DateUtils.getNowDate());
            } else {
                GenUtils.initColumnField(column, table);
            }
            saveColumns.add(column);
        });
        if (CollUtil.isNotEmpty(saveColumns)) {
            genTableColumnMapper.insertOrUpdateBatch(saveColumns);
        }

        // 数据库中不存在的字段，待删除
        List<GenTableColumn> delColumns = tableColumns.stream().filter(column -> !dbTableColumnNames.contains(column.getColumnName())).collect(Collectors.toList());
        if (CollUtil.isNotEmpty(delColumns)) {
            List<Long> ids = delColumns.stream().map(GenTableColumn::getColumnId).collect(Collectors.toList());
            genTableColumnMapper.deleteBatchIds(ids);
        }

        // 更新表描述
        List<GenTable> genTables = selectDbTableListByNames(new String[]{tableName});
        if (!genTables.isEmpty()) {
            GenTable genTable = genTables.get(0);
            GenUtils.initTable(genTable, LoginHelper.getUsername());
            table.setFunctionName(genTable.getFunctionName());
            table.setTableComment(genTable.getTableComment());
            baseMapper.updateById(table);
        }
    }

    /**
     * 批量生成代码（下载方式）
     *
     * @param tableNames 表数组
     * @return 数据
     */
    @Override
    public byte[] downloadCode(String[] tableNames) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(outputStream);
        for (String tableName : tableNames) {
            generatorCode(tableName, zip);
        }
        IoUtil.close(zip);
        return outputStream.toByteArray();
    }

    /**
     * 查询表信息并生成代码
     */
    private void generatorCode(String tableName, ZipOutputStream zip) {
        // 查询表信息
        GenTable table = baseMapper.selectGenTableByName(tableName);
        List<Long> menuIds = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            menuIds.add(identifierGenerator.nextId(null).longValue());
        }
        table.setMenuIds(menuIds);
        // 设置主键列信息
        setPkColumn(table);
        // 设置其他信息
        setTableFromOptions(table);

        VelocityInitializer.initVelocity();

        VelocityContext context = VelocityUtils.prepareContext(table);

        // 获取模板列表
        List<String> templates = VelocityUtils.getTemplateList(table);
        for (String template : templates) {
            // 渲染模板
            StringWriter sw = new StringWriter();
            Template tpl = Velocity.getTemplate(template, Constants.UTF8);
            tpl.merge(context, sw);
            try {
                // 添加到zip
                zip.putNextEntry(new ZipEntry(VelocityUtils.getFileName(template, table)));
                IoUtil.write(zip, StandardCharsets.UTF_8, false, sw.toString());
                IoUtil.close(sw);
                zip.flush();
                zip.closeEntry();
            } catch (IOException e) {
                log.error("渲染模板失败，表名：" + table.getTableName(), e);
            }
        }
    }

    /**
     * 修改保存参数校验
     *
     * @param genTable 业务信息
     */
    @Override
    public void validateEdit(GenTable genTable) {
        if (GenConstants.TPL_TREE.equals(genTable.getTplCategory())) {
            String options = JsonUtils.toJsonString(genTable.getParams());
            Dict paramsObj = JsonUtils.parseMap(options);
            if (StringUtils.isEmpty(paramsObj.getStr(GenConstants.TREE_CODE))) {
                throw new ServiceException("树编码字段不能为空");
            } else if (StringUtils.isEmpty(paramsObj.getStr(GenConstants.TREE_PARENT_CODE))) {
                throw new ServiceException("树父编码字段不能为空");
            } else if (StringUtils.isEmpty(paramsObj.getStr(GenConstants.TREE_NAME))) {
                throw new ServiceException("树名称字段不能为空");
            }
        }
    }

    /**
     * 设置主键列信息
     *
     * @param table 业务表信息
     */
    public void setPkColumn(GenTable table) {
        for (GenTableColumn column : table.getColumns()) {
            if (column.isPk()) {
                table.setPkColumn(column);
                break;
            }
        }
        if (ObjectUtil.isNull(table.getPkColumn())) {
            table.setPkColumn(table.getColumns().get(0));
        }
    }

    /**
     * 设置代码生成其他选项值
     *
     * @param genTable 设置后的生成对象
     */
    public void setTableFromOptions(GenTable genTable) {
        Dict paramsObj = JsonUtils.parseMap(genTable.getOptions());
        if (ObjectUtil.isNotNull(paramsObj)) {
            String treeCode = paramsObj.getStr(GenConstants.TREE_CODE);
            String treeParentCode = paramsObj.getStr(GenConstants.TREE_PARENT_CODE);
            String treeName = paramsObj.getStr(GenConstants.TREE_NAME);
            String parentMenuId = paramsObj.getStr(GenConstants.PARENT_MENU_ID);
            String parentMenuName = paramsObj.getStr(GenConstants.PARENT_MENU_NAME);
            Boolean isUseBO = paramsObj.get(GenConstants.IS_USE_BO, true);
            Boolean isUseQuery = paramsObj.get(GenConstants.IS_USE_QUERY, true);
            Boolean isUseVO = paramsObj.get(GenConstants.IS_USE_VO, true);

            genTable.setTreeCode(treeCode);
            genTable.setTreeParentCode(treeParentCode);
            genTable.setTreeName(treeName);
            genTable.setParentMenuId(parentMenuId);
            genTable.setParentMenuName(parentMenuName);
            genTable.setIsUseBO(isUseBO);
            genTable.setIsUseQuery(isUseQuery);
            genTable.setIsUseVO(isUseVO);
        }
    }

    /**
     * 获取代码生成地址
     *
     * @param table    业务表信息
     * @param template 模板文件路径
     * @return 生成地址
     */
    public static String getGenPath(GenTable table, String template) {
        String genPath = table.getGenPath();
        if (StringUtils.equals(genPath, "/")) {
            return System.getProperty("user.dir") + File.separator + "src" + File.separator + VelocityUtils.getFileName(template, table);
        }
        return genPath + File.separator + VelocityUtils.getFileName(template, table);
    }
}
