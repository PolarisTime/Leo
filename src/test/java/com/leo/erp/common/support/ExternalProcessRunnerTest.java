package com.leo.erp.common.support;

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

    private static final class FakeProcess extends Process {

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
