<template>
  <div class="container">
    <t-dialog
      v-model="visible"
      header="审批记录"
      :width="props.width"
      :height="props.height"
      :close-on-overlay-click="false"
    >
      <t-tabs v-model="tabActiveName" class="demo-tabs">
        <t-loading :loading="loading">
          <t-tab-panel label="流程图" name="image" style="height: 68vh">
            <div
              ref="imageWrapperRef"
              class="image-wrapper"
              :style="transformStyle"
              @wheel="handleMouseWheel"
              @mousedown="handleMouseDown"
              @mousemove="handleMouseMove"
              @mouseup="handleMouseUp"
              @mouseleave="handleMouseLeave"
              @dblclick="resetTransform"
            >
              <t-card class="box-card">
                <t-image :src="imgUrl" class="scalable-image" />
              </t-card>
            </div>
          </t-tab-panel>
        </t-loading>
        <t-tab-panel label="审批信息" value="info">
          <t-loading :loading="loading">
            <t-table :data="historyList" row-key="id" :columns="columns" style="width: 100%">
              <template #approveName="{ row }">
                <template v-if="row.approveName">
                  <t-tag
                    v-for="(item, index) in row.approveName.split(',')"
                    :key="index"
                    theme="success"
                    variant="light"
                  >
                    {{ item }}
                  </t-tag>
                </template>
                <template v-else> <t-tag type="success" variant="light">无</t-tag></template>
              </template>
              <template #flowStatus="{ row }">
                <dict-tag :options="wf_task_status" :value="row.flowStatus"></dict-tag>
              </template>
              <template #attachmentList="{ row }">
                <t-popup
                  v-if="row.attachmentList && row.attachmentList.length > 0"
                  placement="right"
                  :overlay-style="{ width: '310px' }"
                  trigger="click"
                >
                  <t-button style="margin-right: 16px">附件</t-button>
                  <template #content>
                    <t-table :data="row.attachmentList" :columns="attachmentListColumns">
                      <template #name="scope">
                        <t-button variant="text" @click="handleDownload(scope.row.ossId)">下载</t-button>
                      </template>
                    </t-table>
                  </template>
                </t-popup>
              </template>
            </t-table>
          </t-loading>
        </t-tab-panel>
      </t-tabs>
    </t-dialog>
  </div>
</template>
<script lang="ts" setup>
import type { PrimaryTableCol } from 'tdesign-vue-next';
import { ref } from 'vue';

import { listByIds } from '@/api/system/oss';
import { flowImage } from '@/api/workflow/instance';

const { proxy } = getCurrentInstance();
const { wf_task_status } = proxy.useDict('wf_task_status');
const props = defineProps({
  width: {
    type: String,
    default: '80%',
  },
  height: {
    type: String,
    default: '100%',
  },
});
const loading = ref(false);
const visible = ref(false);
const historyList = ref<Array<any>>([]);
const tabActiveName = ref('image');
const imgUrl = ref('');

// 列显隐信息
const columns = computed<Array<PrimaryTableCol>>(() => [
  { title: `序号`, colKey: 'serial-number', width: 70 },
  { title: `任务名称`, colKey: 'nodeName', align: 'center' },
  { title: `办理人`, colKey: 'approveName', align: 'center', ellipsis: true },
  { title: `状态`, colKey: 'flowStatus', align: 'center', width: 80 },
  { title: `审批意见`, colKey: 'message', align: 'center', ellipsis: true },
  { title: `开始时间`, colKey: 'createTime', align: 'center', width: '10%', minWidth: 112 },
  { title: `结束时间`, colKey: 'updateTime', align: 'center', width: '10%', minWidth: 112 },
  { title: `运行时长`, colKey: 'runDuration', align: 'center' },
  { title: `附件`, colKey: 'attachmentList', align: 'center' },
]);
// 列显隐信息
const attachmentListColumns = computed<Array<PrimaryTableCol>>(() => [
  { title: `附件名称`, colKey: 'originalName', align: 'center', width: 202, ellipsis: true },
  { title: `操作`, colKey: 'name', align: 'center', width: 80 },
]);

// 初始化查询审批记录
const init = async (businessId: string | number) => {
  visible.value = true;
  loading.value = true;
  tabActiveName.value = 'image';
  historyList.value = [];
  flowImage(businessId).then((resp) => {
    if (resp.data) {
      historyList.value = resp.data.list;
      imgUrl.value = `data:image/gif;base64,${resp.data.image}`;
      if (historyList.value.length > 0) {
        historyList.value.forEach((item) => {
          if (item.ext) {
            getIds(item.ext).then((res) => {
              item.attachmentList = res.data;
            });
          } else {
            item.attachmentList = [];
          }
        });
      }
      loading.value = false;
    }
  });
};
const getIds = async (ids: string | string[]) => {
  return listByIds(ids);
};

/** 下载按钮操作 */
const handleDownload = (ossId: string) => {
  proxy?.$download.oss(ossId);
};

const imageWrapperRef = ref<HTMLElement | null>(null);
const scale = ref(1); // 初始缩放比例
const maxScale = 3; // 最大缩放比例
const minScale = 0.5; // 最小缩放比例

let isDragging = false;
let startX = 0;
let startY = 0;
let currentTranslateX = 0;
let currentTranslateY = 0;

const handleMouseWheel = (event: WheelEvent) => {
  event.preventDefault();
  let newScale = scale.value - event.deltaY / 1000;
  newScale = Math.max(minScale, Math.min(newScale, maxScale));
  if (newScale !== scale.value) {
    scale.value = newScale;
    resetDragPosition(); // 重置拖拽位置，使图片居中
  }
};

const handleMouseDown = (event: MouseEvent) => {
  if (scale.value > 1) {
    event.preventDefault(); // 阻止默认行为，防止拖拽
    isDragging = true;
    startX = event.clientX;
    startY = event.clientY;
  }
};

const handleMouseMove = (event: MouseEvent) => {
  if (!isDragging || !imageWrapperRef.value) return;

  const deltaX = event.clientX - startX;
  const deltaY = event.clientY - startY;
  startX = event.clientX;
  startY = event.clientY;

  currentTranslateX += deltaX;
  currentTranslateY += deltaY;

  // 边界检测，防止图片被拖出容器
  const bounds = getBounds();
  if (currentTranslateX > bounds.maxTranslateX) {
    currentTranslateX = bounds.maxTranslateX;
  } else if (currentTranslateX < bounds.minTranslateX) {
    currentTranslateX = bounds.minTranslateX;
  }

  if (currentTranslateY > bounds.maxTranslateY) {
    currentTranslateY = bounds.maxTranslateY;
  } else if (currentTranslateY < bounds.minTranslateY) {
    currentTranslateY = bounds.minTranslateY;
  }

  applyTransform();
};

const handleMouseUp = () => {
  isDragging = false;
};

const handleMouseLeave = () => {
  isDragging = false;
};

const resetTransform = () => {
  scale.value = 1;
  currentTranslateX = 0;
  currentTranslateY = 0;
  applyTransform();
};

const resetDragPosition = () => {
  currentTranslateX = 0;
  currentTranslateY = 0;
  applyTransform();
};

const applyTransform = () => {
  if (imageWrapperRef.value) {
    imageWrapperRef.value.style.transform = `translate(${currentTranslateX}px, ${currentTranslateY}px) scale(${scale.value})`;
  }
};

const getBounds = () => {
  if (!imageWrapperRef.value) return { minTranslateX: 0, maxTranslateX: 0, minTranslateY: 0, maxTranslateY: 0 };

  const imgRect = imageWrapperRef.value.getBoundingClientRect();
  const containerRect = imageWrapperRef.value.parentElement?.getBoundingClientRect() ?? imgRect;

  const minTranslateX = (containerRect.width - imgRect.width * scale.value) / 2;
  const maxTranslateX = -(containerRect.width - imgRect.width * scale.value) / 2;
  const minTranslateY = (containerRect.height - imgRect.height * scale.value) / 2;
  const maxTranslateY = -(containerRect.height - imgRect.height * scale.value) / 2;

  return { minTranslateX, maxTranslateX, minTranslateY, maxTranslateY };
};

const transformStyle = computed(() => ({
  transition: isDragging ? 'none' : 'transform 0.2s ease',
}));

/**
 * 对外暴露子组件方法
 */
defineExpose({
  init,
});
</script>
<style lang="less" scoped>
.triangle {
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.3);
  border-radius: 6px;
}

.triangle::after {
  content: ' ';
  position: absolute;
  top: 8em;
  right: 215px;
  border: 15px solid;
  border-color: transparent #fff transparent transparent;
}

.container {
  :deep(.t-dialog__ctx .t-dialog__body) {
    max-height: calc(100vh - 170px) !important;
    min-height: calc(100vh - 170px) !important;
  }
}

.image-wrapper {
  width: 100%;
  overflow: hidden;
  position: relative;
  margin: 0 auto;
  display: flex;
  justify-content: center;
  align-items: center;
  user-select: none; /* 禁用文本选择 */
  cursor: grab; /* 设置初始鼠标指针为可拖动 */
}

.image-wrapper:active {
  cursor: grabbing; /* 当正在拖动时改变鼠标指针 */
}

.scalable-image {
  object-fit: contain;
  width: 100%;
  padding: 15px;
}
</style>
