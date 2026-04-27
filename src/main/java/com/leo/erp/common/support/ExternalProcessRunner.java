package com.leo.erp.common.support;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class ExternalProcessRunner {

    public ProcessResult run(ProcessBuilder processBuilder, Duration timeout, String actionName) throws IOException, InterruptedException {
        Process process = start(processBuilder);
        CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> readOutput(process.getInputStream()));

        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            outputFuture.cancel(true);
            throw new IOException(actionName + " 超时（" + timeout.toMinutes() + "分钟）");
        }

        String output = joinOutput(outputFuture);
        if (process.exitValue() != 0) {
            throw new IOException(actionName + " 失败: " + output);
        }
        return new ProcessResult(process.exitValue(), output);
    }

    protected Process start(ProcessBuilder processBuilder) throws IOException {
        return processBuilder.start();
    }

    private String joinOutput(CompletableFuture<String> outputFuture) throws IOException, InterruptedException {
        try {
            return outputFuture.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("读取进程输出失败", cause);
        } catch (TimeoutException ex) {
            throw new IOException("读取进程输出超时", ex);
        }
    }

    private String readOutput(InputStream inputStream) {
        try (InputStream in = inputStream) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "读取进程输出失败");
        }
    }

    public record ProcessResult(int exitCode, String output) {
    }
}
