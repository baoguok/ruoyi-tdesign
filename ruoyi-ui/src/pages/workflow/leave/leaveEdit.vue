<template>
  <div>
    <t-space direction="vertical" class="w100%">
      <t-card>
        <div style="display: flex; justify-content: space-between">
          <div>
            <t-button v-if="submitButtonShow" :loading="buttonLoading" theme="default" @click="submitForm('draft')">
              暂存
            </t-button>
            <t-button v-if="submitButtonShow" :loading="buttonLoading" theme="primary" @click="submitForm('submit')">
              提 交
            </t-button>
            <t-button v-if="approvalButtonShow" :loading="buttonLoading" theme="primary" @click="approvalVerifyOpen">
              审批
            </t-button>
            <t-button v-if="form && form.id && form.status !== 'draft'" theme="primary" @click="handleApprovalRecord">
              流程进度
            </t-button>
          </div>
          <div>
            <t-button style="float: right" @click="goBack()">返回</t-button>
          </div>
        </div>
      </t-card>
      <t-card style="height: 70vh; overflow-y: auto">
        <t-loading :loading="loading">
          <t-form
            ref="leaveFormRef"
            :disabled="routeParams.type === 'view'"
            :data="form"
            :rules="rules"
            label-width="80px"
          >
            <t-form-item label="请假类型" name="leaveType">
              <t-select v-model="form.leaveType" placeholder="请选择请假类型" style="width: 100%">
                <t-option v-for="item in options" :key="item.value" :label="item.label" :value="item.value" />
              </t-select>
            </t-form-item>
            <t-form-item label="请假时间" required-mark>
              <t-date-range-picker
                v-model="leaveTime"
                value-type="YYYY-MM-DD HH:mm:ss"
                allow-input
                clearable
                separator="至"
                :placeholder="['开始日期', '结束日期']"
                @change="changeLeaveTime()"
              />
            </t-form-item>
            <t-form-item label="请假天数" name="leaveDays">
              <t-input-number v-model="form.leaveDays" theme="normal" disabled placeholder="请输入" />
            </t-form-item>
            <t-form-item label="请假原因" name="remark">
              <t-textarea v-model="form.remark" placeholder="请输入请假原因" />
            </t-form-item>
          </t-form>
        </t-loading>
      </t-card>
    </t-space>
    <!-- 提交组件 -->
    <submit-verify ref="submitVerifyRef" :task-variables="taskVariables" @submit-callback="submitCallback" />
    <!-- 审批记录 -->
    <approval-record ref="approvalRecordRef" />
    <t-dialog
      v-model:visible="visible"
      :header="title"
      width="500"
      :close-on-overlay-click="false"
      @before-close="handleClose"
    >
      <t-select v-model="flowCode" placeholder="Select" style="width: 240px">
        <t-option v-for="item in flowCodeOptions" :key="item.value" :label="item.label" :value="item.value" />
      </t-select>
      <template #footer>
        <div class="dialog-footer">
          <t-button theme="default" @click="handleClose">取消</t-button>
          <t-button theme="primary" @click="submitFlow()"> 确认 </t-button>
        </div>
      </template>
    </t-dialog>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'LeaveEdit',
});

import type { FormInstanceFunctions, FormRule } from 'tdesign-vue-next';
import { ref } from 'vue';

import type { R } from '@/api/model/resultModel';
import { addLeave, getLeave, updateLeave } from '@/api/workflow/leave';
import type { LeaveForm, LeaveVo } from '@/api/workflow/model/leaveModel';
import type { StartProcessBo } from '@/api/workflow/model/taskModel';
import { startWorkFlow } from '@/api/workflow/task';
import ApprovalRecord from '@/components/Process/approvalRecord.vue';
import SubmitVerify from '@/components/Process/submitVerify.vue';
import { useTabsRouterStore } from '@/store';

const { proxy } = getCurrentInstance();

const route = useRoute();
const removeCurrentTab = useTabsRouterStore().useRemoveCurrentTab();
const buttonLoading = ref(false);
const loading = ref(true);
const leaveTime = ref<Array<string>>([]);
// 路由参数
const routeParams = ref<Record<string, any>>({});
const options = [
  {
    value: '1',
    label: '事假',
  },
  {
    value: '2',
    label: '调休',
  },
  {
    value: '3',
    label: '病假',
  },
  {
    value: '4',
    label: '婚假',
  },
];
const flowCodeOptions = [
  {
    value: 'leave1',
    label: '请假申请-普通',
  },
  {
    value: 'leave2',
    label: '请假申请-排他网关',
  },
  {
    value: 'leave3',
    label: '请假申请-并行网关',
  },
  {
    value: 'leave4',
    label: '请假申请-会签',
  },
  {
    value: 'leave5',
    label: '请假申请-并行会签网关',
  },
];

const flowCode = ref<string>('');

const visible = ref(false);
const title = ref('流程定义');
// 提交组件
const submitVerifyRef = ref<InstanceType<typeof SubmitVerify>>();
// 审批记录组件
const approvalRecordRef = ref<InstanceType<typeof ApprovalRecord>>();

const leaveFormRef = ref<FormInstanceFunctions>();

const submitFormData = ref<StartProcessBo>({
  businessId: '',
  flowCode: '',
  variables: {},
});
const taskVariables = ref<Record<string, any>>({});

// 校验规则
const rules = ref<Record<string, Array<FormRule>>>({
  leaveType: [{ required: true, message: '请假类型不能为空' }],
  leaveDays: [{ required: true, message: '请假天数不能为空' }],
  remark: [{ max: 255, message: '请假原因不能超过255个字符' }],
});

const handleClose = () => {
  visible.value = false;
  flowCode.value = '';
  buttonLoading.value = false;
};
const form = ref<LeaveVo & LeaveForm>({});

/** 表单重置 */
const reset = () => {
  form.value = {};
  leaveTime.value = [];
  leaveTime.value = [];
  submitFormData.value = {
    businessId: '',
    flowCode: '',
    variables: {},
  };
  proxy.resetForm('leaveFormRef');
};

const changeLeaveTime = () => {
  const startDate = new Date(leaveTime.value[0]);
  startDate.setHours(0, 0, 0, 0);
  const startTime = startDate.getTime();
  const endDate = new Date(leaveTime.value[1]);
  endDate.setHours(0, 0, 0, 0);
  const endTime = endDate.getTime();
  const diffInMilliseconds = endTime - startTime;
  form.value.leaveDays = Math.floor(diffInMilliseconds / (1000 * 60 * 60 * 24)) + 1;
  form.value.startDate = leaveTime.value[0];
  form.value.endDate = leaveTime.value[1];
};
/** 获取详情 */
const getInfo = () => {
  loading.value = true;
  buttonLoading.value = false;
  nextTick(async () => {
    const res = await getLeave(routeParams.value.id);
    Object.assign(form.value, res.data);
    leaveTime.value = [];
    leaveTime.value.push(form.value.startDate);
    leaveTime.value.push(form.value.endDate);
    loading.value = false;
    buttonLoading.value = false;
  });
};

/** 提交按钮 */
const submitForm = async (status: string) => {
  if (leaveTime.value.length === 0) {
    proxy?.$modal.msgError('请假时间不能为空');
    return;
  }
  try {
    const result = await leaveFormRef.value?.validate();
    if (result === true) {
      buttonLoading.value = true;
      let res: R<LeaveVo>;
      if (form.value.id) {
        res = await updateLeave(form.value);
      } else {
        res = await addLeave(form.value);
      }
      form.value = res.data;
      if (status === 'draft') {
        buttonLoading.value = false;
        proxy?.$modal.msgSuccess('暂存成功');
        removeCurrentTab();
      } else {
        if (
          (form.value.status === 'draft' && (flowCode.value === '' || flowCode.value === null)) ||
          routeParams.value.type === 'add'
        ) {
          flowCode.value = flowCodeOptions[0].value;
          visible.value = true;
          return;
        }
        // 说明启动过先随意传个参数
        if (flowCode.value === '' || flowCode.value === null) {
          flowCode.value = 'xx';
        }
        await handleStartWorkFlow(res.data);
      }
    }
  } finally {
    buttonLoading.value = false;
  }
};

const submitFlow = async () => {
  handleStartWorkFlow(form.value);
};
// 提交申请
const handleStartWorkFlow = async (data: LeaveForm) => {
  try {
    submitFormData.value.flowCode = flowCode.value;
    submitFormData.value.businessId = data.id;
    // 流程变量
    taskVariables.value = {
      leaveDays: data.leaveDays,
      userList: ['1', '3', '4'],
    };
    submitFormData.value.variables = taskVariables.value;
    const resp = await startWorkFlow(submitFormData.value);
    visible.value = false;
    if (submitVerifyRef.value) {
      buttonLoading.value = false;
      submitVerifyRef.value.openDialog(resp.data.taskId);
    }
  } finally {
    buttonLoading.value = false;
  }
};
// 审批记录
const handleApprovalRecord = () => {
  approvalRecordRef.value.init(form.value.id);
};
// 提交回调
const submitCallback = async () => {
  removeCurrentTab();
};

// 返回
const goBack = () => {
  removeCurrentTab();
};
// 审批
const approvalVerifyOpen = async () => {
  submitVerifyRef.value.openDialog(routeParams.value.taskId);
};
// 校验提交按钮是否显示
const submitButtonShow = computed(() => {
  return (
    routeParams.value.type === 'add' ||
    (routeParams.value.type === 'update' &&
      form.value.status &&
      (form.value.status === 'draft' || form.value.status === 'cancel' || form.value.status === 'back'))
  );
});

// 校验审批按钮是否显示
const approvalButtonShow = computed(() => {
  return routeParams.value.type === 'approval' && form.value.status && form.value.status === 'waiting';
});

onMounted(() => {
  nextTick(async () => {
    routeParams.value = route.query;
    reset();
    loading.value = false;
    if (
      routeParams.value.type === 'update' ||
      routeParams.value.type === 'view' ||
      routeParams.value.type === 'approval'
    ) {
      getInfo();
    }
  });
});
</script>
