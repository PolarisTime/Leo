import { createLogger, type Logger } from '../src/logger.js'

/** 静默日志：测试里不打印 JSON 行，避免干扰 TAP 输出。 */
export function silentLogger(): Logger {
  return createLogger({ level: 'error', sink: () => {} })
}

/** 收集日志（需要断言日志内容时用）。 */
export function collectingLogger(): { logger: Logger; lines: string[] } {
  const lines: string[] = []
  return {
    lines,
    logger: createLogger({
      level: 'debug',
      sink: (line) => {
        lines.push(line)
      },
    }),
  }
}
