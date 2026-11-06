/**
 * Renderer'ın main process ile tek konuşma noktası. window.cevher'in döndürdüğü
 * IpcResult zarfını açar: başarıda veriyi döndürür, hatada AppError fırlatır.
 */

import { AppError } from '@shared/errors'
import type { EventChannel, IpcEventContract, InvokeArgs, InvokeChannel, InvokeReturn } from '@shared/ipc'

export async function call<C extends InvokeChannel>(channel: C, ...args: InvokeArgs<C>): Promise<InvokeReturn<C>> {
  const result = await window.cevher.invoke(channel, ...args)
  if (!result.ok) throw AppError.from(result.error)
  return result.data
}

export function subscribe<E extends EventChannel>(
  event: E,
  listener: (payload: IpcEventContract[E]) => void
): () => void {
  return window.cevher.on(event, listener)
}
