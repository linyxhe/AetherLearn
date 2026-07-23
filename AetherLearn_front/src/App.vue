<template>
  <n-message-provider>
    <n-dialog-provider>
      <n-notification-provider>
        <router-view />
      </n-notification-provider>
    </n-dialog-provider>
  </n-message-provider>
</template>

<script setup>
import { NDialogProvider, NMessageProvider, NNotificationProvider } from 'naive-ui'
</script>
