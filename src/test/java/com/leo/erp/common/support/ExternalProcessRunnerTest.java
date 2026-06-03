package com.leo.erp.common.support;

import com.leo.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExternalProcessRunnerTest {

    @Test
    void shouldReturnOutputWhenProcessFinishesNormally() throws Exception {
        FakeProcess process = new FakeProcess(true, 0, "ok");
        ExternalProcessRunner runner = new TestRunner(process);

        ExternalProcessRunner.ProcessResult result = runner.run(new ProcessBuilder("echo", "ok"), Duration.ofMillis(100), "echo");

        assertThat(result.output()).isEqualTo("ok");
        assertThat(result.exitCode()).isEqualTo(0);
    }

    @Test
    void shouldDestroyProcessWhenTimedOut() {
        FakeProcess process = new FakeProcess(false, 0, "");
        ExternalProcessRunner runner = new TestRunner(process);

        assertThatThrownBy(() -> runner.run(new ProcessBuilder("sleep", "1"), Duration.ofMillis(10), "sleep"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("超时");
        assertThat(process.destroyedForcibly).isTrue();
    }

    @Test
    void shouldThrowWhenProcessExitsWithNonZeroCode() {
        FakeProcess process = new FakeProcess(true, 1, "error output");
        ExternalProcessRunner runner = new TestRunner(process);

        assertThatThrownBy(() -> runner.run(new ProcessBuilder("cmd"), Duration.ofSeconds(5), "test-cmd"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("失败")
                .hasMessageContaining("error output");
    }

    @Test
    void shouldThrowWhenOutputReadFailsWithIoException() {
        FakeProcess process = new FakeProcess(true, 0, "ok") {
            @Override
            public InputStream getInputStream() {
                return new InputStream() {
                    @Override
                    public int read() throws IOException {
                        throw new IOException("stream broken");
                    }

                    @Override
                    public byte[] readAllBytes() throws IOException {
                        throw new IOException("stream broken");
                    }
                };
            }
        };
        ExternalProcessRunner runner = new TestRunner(process);

        assertThatThrownBy(() -> runner.run(new ProcessBuilder("cmd"), Duration.ofSeconds(5), "test"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("读取进程输出失败");
    }

    @Test
    void processResultShouldContainExitCodeAndOutput() {
        ExternalProcessRunner.ProcessResult result = new ExternalProcessRunner.ProcessResult(0, "hello");
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.output()).isEqualTo("hello");
    }

    private static final class TestRunner extends ExternalProcessRunner {

        private final Process process;

        private TestRunner(Process process) {
            this.process = process;
        }

        @Override
        protected Process start(ProcessBuilder processBuilder) {
            return process;
        }
    }

    private static class FakeProcess extends Process {

        private final boolean finished;
        private final int exitCode;
        private final InputStream inputStream;
        private boolean destroyedForcibly;

        private FakeProcess(boolean finished, int exitCode, String output) {
            this.finished = finished;
            this.exitCode = exitCode;
            this.inputStream = new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return inputStream;
        }

        @Override
        public InputStream getErrorStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public int waitFor() {
            return exitCode;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            return finished;
        }

        @Override
        public int exitValue() {
            return exitCode;
        }

        @Override
        public void destroy() {
            destroyedForcibly = true;
        }

        @Override
        public Process destroyForcibly() {
            destroyedForcibly = true;
            return this;
        }

        @Override
        public boolean isAlive() {
            return !finished && !destroyedForcibly;
        }
    }
}
