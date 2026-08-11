package ai.nooa.cli;

import org.junit.jupiter.api.*;

import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("QueueManager")
class QueueManagerTest {

    @Test
    @DisplayName("submit and drain messages")
    void submitAndDrain() {
        var qm = new QueueManager();
        qm.submit("hello");
        qm.submit("world");

        var items = qm.drain();
        assertThat(items).hasSize(2);
        assertThat(items.get(0).payload()).isEqualTo("hello");
    }

    @Test
    @DisplayName("named channels route independently")
    void namedChannels() {
        var qm = new QueueManager();
        qm.submit("slash_commands", "/help");
        qm.submit("user_messages", "hello");

        var channel = qm.channel("slash_commands");
        channel.send("/clear");

        var items = qm.drain();
        assertThat(items).hasSize(2);
    }

    @Test
    @DisplayName("poll waits for messages")
    void pollWaits() {
        var qm = new QueueManager();
        var executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            qm.submit("delayed");
        });

        var result = qm.poll(5000);
        assertThat(result).isPresent();
        assertThat(result.get().payload()).isEqualTo("delayed");

        executor.shutdown();
    }

    @Test
    @DisplayName("channel drain clears pending")
    void channelDrain() {
        var channel = new QueueManager.Channel("test");
        channel.send("a");
        channel.send("b");

        assertThat(channel.size()).isEqualTo(2);
        var items = channel.drain();
        assertThat(items).hasSize(2);
        assertThat(channel.isEmpty()).isTrue();
    }
}
