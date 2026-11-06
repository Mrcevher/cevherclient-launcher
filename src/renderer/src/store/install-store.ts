import { create } from 'zustand'
import type { InstallTask } from '@shared/types'
import i18n from '../i18n'
import { call, subscribe } from '../lib/api'
import { reportError } from '../lib/errors'
import { versionLabelFromKey } from '../lib/format'
import { useNotifyStore } from './notify-store'

interface InstallStore {
  /** Aktif (indiriliyor / duraklatılmış / sırada) görevler */
  tasks: Record<string, InstallTask>
  installed: string[]
  /** Olay aboneliğini başlatır; aboneliği iptal eden fonksiyonu döndürür. */
  init(): () => void
  start(versionKey: string): Promise<void>
  pause(taskId: string): Promise<void>
  resume(taskId: string): Promise<void>
  cancel(taskId: string): Promise<void>
}

export function isActiveTask(task: InstallTask): boolean {
  return task.state === 'downloading' || task.state === 'paused' || task.state === 'queued'
}

export const useInstallStore = create<InstallStore>((set) => {
  /** Main'den gelen her ilerleme olayını işler. */
  function onProgress(task: InstallTask): void {
    if (isActiveTask(task)) {
      set((s) => ({ tasks: { ...s.tasks, [task.id]: task } }))
      return
    }
    // Bitmiş görevleri listeden çıkar.
    set((s) => {
      const rest = { ...s.tasks }
      delete rest[task.id]
      return {
        tasks: rest,
        installed:
          task.state === 'completed' && !s.installed.includes(task.versionKey)
            ? [...s.installed, task.versionKey]
            : s.installed
      }
    })
    if (task.state === 'completed') {
      useNotifyStore.getState().push({
        kind: 'success',
        title: i18n.t('versions.completedTitle'),
        message: i18n.t('versions.completedMessage', { version: versionLabelFromKey(task.versionKey) })
      })
    } else if (task.state === 'failed') {
      reportError(task.error ?? { code: 'DOWNLOAD_FAILED' })
    }
  }

  async function run(action: () => Promise<unknown>): Promise<void> {
    try {
      await action()
    } catch (err) {
      reportError(err)
    }
  }

  return {
    tasks: {},
    installed: [],

    init() {
      const unsubscribe = subscribe('install:progress', onProgress)
      void run(async () => {
        const [tasks, installed] = await Promise.all([call('install:listTasks'), call('install:listInstalled')])
        set({
          tasks: Object.fromEntries(tasks.filter(isActiveTask).map((t) => [t.id, t])),
          installed
        })
      })
      return unsubscribe
    },

    start: (versionKey) => run(() => call('install:start', versionKey)),
    pause: (taskId) => run(() => call('install:pause', taskId)),
    resume: (taskId) => run(() => call('install:resume', taskId)),
    cancel: (taskId) => run(() => call('install:cancel', taskId))
  }
})
