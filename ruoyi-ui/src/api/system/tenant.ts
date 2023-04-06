import { request } from '@/utils/request';
import { R, TableDataInfo } from '@/api/model/resultModel';
import { SysTenantBo, SysTenantVo } from '@/api/system/model/tenantModel';

// 查询租户列表
export function listTenant(query: SysTenantBo) {
  return request.get<TableDataInfo<SysTenantVo>>({
    url: '/system/tenant/list',
    params: query,
  });
}

// 查询租户详细
export function getTenant(id) {
  return request.get<R<SysTenantVo>>({
    url: `/system/tenant/${id}`,
  });
}

// 新增租户
export function addTenant(data: SysTenantBo) {
  return request.post<R<void>>({
    url: '/system/tenant',
    data,
  });
}

// 修改租户
export function updateTenant(data: SysTenantBo) {
  return request.put<R<void>>({
    url: '/system/tenant',
    data,
  });
}

// 租户状态修改
export function changeTenantStatus(id: number, tenantId: string, status: string) {
  const data = {
    id,
    tenantId,
    status,
  };
  return request.put<R<void>>({
    url: '/system/tenant/changeStatus',
    data,
  });
}

// 删除租户
export function delTenant(id: string) {
  return request.delete<R<void>>({
    url: `/system/tenant/${id}`,
  });
}

// 动态切换租户
export function dynamicTenant(tenantId: string | number) {
  return request.get<R<void>>({
    url: `/system/tenant/dynamic/${tenantId}`,
  });
}

// 清除动态租户
export function dynamicClear() {
  return request.get<R<void>>({
    url: '/system/tenant/dynamic/clear',
  });
}

// 同步租户套餐
export function syncTenantPackage(tenantId, packageId) {
  const data = {
    tenantId,
    packageId,
  };
  return request.get<R<void>>({
    url: '/system/tenant/syncTenantPackage',
    params: data,
  });
}