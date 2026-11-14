/**
 * JSON 行日志：一行一个对象，便于 `journalctl -o cat | jq` 与集中式采集。
 *
 * <p>约束（对齐设计文档 §12）：日志里**不出现价格值、不出现票据原文**，
 * 只记 room / user / 字节数 / 结果这类元信息。</p>
 */

import type { LogLevel } from './config.js'

const LEVEL_WEIGHT: Record<LogLevel, number> = {
  debug: 10,
  info: 20,
  warn: 30,
  error: 40,
}

export type LogFields = Record<string, unknown>

export interface Logger {
  debug(message: string, fields?: LogFields): void
  info(message: string, fields?: LogFields): void
  warn(message: string, fields?: LogFields): void
  error(message: string, fields?: LogFields): void
  child(fields: LogFields): Logger
}

function errorToFields(error: unknown): LogFields {
  if (error instanceof Error) {
    return { error: error.message, errorName: error.name }
  }
  return { error: String(error) }
}

export interface LoggerOptions {
  level?: LogLevel
  /** 输出目标（测试里可注入 spy）；默认写 stdout。 */
  sink?: (line: string) => void
  /** 固定字段（例如 service: 'price-collab'）。 */
  base?: LogFields
  now?: () => Date
}

export function createLogger(options: LoggerOptions = {}): Logger {
  const level = options.level ?? 'info'
  const threshold = LEVEL_WEIGHT[level]
  const sink =
    options.sink ??
    ((line: string) => {
      process.stdout.write(`${line}\n`)
    })
  const now = options.now ?? (() => new Date())
  const base = options.base ?? {}

  const write = (entryLevel: LogLevel, message: string, fields?: LogFields) => {
    if (LEVEL_WEIGHT[entryLevel] < threshold) return
    const payload: LogFields = {
      ts: now().toISOString(),
      level: entryLevel,
      msg: message,
      ...base,
      ...fields,
    }
    sink(JSON.stringify(payload))
  }

  return {
    debug: (message, fields) => write('debug', message, fields),
    info: (message, fields) => write('info', message, fields),
    warn: (message, fields) => write('warn', message, fields),
    error: (message, fields) =>
      write('error', message, fields === undefined ? undefined : fields),
    child: (fields) =>
      createLogger({
        level,
        sink,
        now,
        base: { ...base, ...fields },
      }),
  }
}

export function logErrorFields(error: unknown): LogFields {
  return errorToFields(error)
}
