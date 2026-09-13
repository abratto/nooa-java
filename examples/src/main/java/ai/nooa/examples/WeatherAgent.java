package ai.nooa.examples;

import ai.nooa.Agent;
import ai.nooa.AgentFactory;
import ai.nooa.annotations.Generate;
import ai.nooa.annotations.Hidden;
import ai.nooa.annotations.Strategy;
import ai.nooa.annotations.SystemPrompt;
import ai.nooa.llm.UnifiedLLM;
import ai.nooa.strategy.PredictStrategy;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * WeatherAgent — San Rafael, CA 7-day forecast agent.
 *
 * <p>This is the reference example for a production-shaped agent: every
 * framework feature is used where it earns its place, and nothing else.</p>
 *
 * <p><b>NOOA features demonstrated</b></p>
 * <ul>
 *   <li>Deterministic data collection in plain Java — {@link #fetchForecast()}
 *       performs the HTTP calls and JSON parsing. Network I/O has a documented
 *       contract and gains nothing from a model call.</li>
 *   <li>{@code @Hidden} dependencies — {@link #http} and {@link #json} are
 *       excluded from the model-visible capability surface; the model should
 *       reason about forecast records, not operate the HTTP client.</li>
 *   <li>Typed I/O boundary — {@link DailyForecast} is the record-shaped
 *       contract between deterministic code and generation; the runtime
 *       serializes the typed values into the model call.</li>
 *   <li>Two-stage typed generation — {@link #noteworthyDays(List)} picks the
 *       periods that matter ({@link NoteworthyDays}), and
 *       {@link #weeklyBrief(List, NoteworthyDays)} writes the human-facing
 *       brief ({@link WeeklyBrief}). Each call has one narrow responsibility
 *       and a schema the runtime enforces.</li>
 *   <li>PredictStrategy on both generated methods — structured output with
 *       retry-on-invalid, so malformed model replies never reach the caller.</li>
 *   <li>Java orchestrator — {@link #weeklyBriefForSanRafael()} sequences the
 *       three steps; application control flow stays ordinary Java.</li>
 *   <li>Strict system prompt — the schema wording in {@code @SystemPrompt}
 *       reinforces the record types, which matters with local models.</li>
 *   <li>Reasoning effort — judging 14 forecast periods and writing a brief is
 *       genuine judgement: {@code "medium"} balances quality and latency.</li>
 * </ul>
 *
 * <p><b>Run</b> (this class has its own {@code main}; needs network access to
 * {@code api.weather.gov}, which is public and key-free):
 * <pre>{@code
 * mvn -pl examples exec:java -Dexec.mainClass=ai.nooa.examples.WeatherAgent
 * }</pre>
 * The example uses Ollama by default. Set {@code NOOA_MODEL} to select a local
 * model, or set {@code OPENAI_API_KEY} and optionally {@code OPENAI_MODEL} to
 * use OpenAI. {@code NOOA_BASE_URL}, {@code NOOA_API_KEY}, and {@code NOOA_MODEL}
 * can point it at any OpenAI-compatible endpoint.</p>
 *
 * The {@code prompt} value on each @Generate method is the runtime instruction;
 * ordinary Javadoc remains documentation for readers and generated API docs.
 */
@SystemPrompt("""
    You are a concise weather briefing agent for a Bay Area user.
    Be concrete and practical: name extremes, rain chances and any
    actions worth taking. Do not repeat every day's forecast verbatim.

    For every structured response, obey the Java record field types exactly.
    NoteworthyDays must be exactly:
    {"names":["Tuesday"],"reason":"One short sentence."}
    Here "names" is an array of strings and "reason" is one plain JSON
    string; never return "reason" as an array. WeeklyBrief must be exactly:
    {"headline":"...","body":"...","advice":["..."]}
    Do not include NoteworthyDays fields such as "names" or "reason" in a
    WeeklyBrief response.
    """)
public class WeatherAgent extends Agent {

    private static final double LAT = 37.9735;
    private static final double LON = -122.5311;
    private static final int MAX_FORECAST_PERIODS = 14;

    @Hidden private final HttpClient http;
    @Hidden private final ObjectMapper json;

    public WeatherAgent(UnifiedLLM llm) {
        super(llm);
        // These dependencies are ordinary Java state. @Hidden keeps them out
        // of the model-visible capability surface because the model should
        // receive forecast records, not operate the HTTP client directly.
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.json = new ObjectMapper();
        // Two judgement steps over 14 typed periods: medium effort.
        ExampleLLM.tune(this, "medium", 2048);
    }

    // ---------- typed I/O surface ----------

    /**
     * One forecast period from the NWS periods collection.
     *
     * <p>A record is the boundary between deterministic code and generation:
     * Java creates these values from JSON, while NOOA serializes their typed
     * shape when building a model call.</p>
     */
    public record DailyForecast(
        String shortName,        // e.g. "Monday", "Monday Night"
        int temperature,         // e.g. 72
        String windSpeed,        // e.g. "10 mph"
        String windDirection,    // e.g. "SW"
        String summary)          // short period description
    {}

    /** Model judgement: which periods most deserve attention, and why. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NoteworthyDays(
        List<String> names,      // shortName values of noteworthy periods
        String reason)           // single sentence explaining why they matter
    {
        public NoteworthyDays {
            names = names == null ? List.of() : List.copyOf(names);
        }
    }

    /**
     * Model output: final human-facing brief. A record makes the output easy
     * for the caller to render without parsing model-generated prose.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WeeklyBrief(
        String headline,         // one-sentence takeaway for the week
        String body,             // 3-5 sentence practical summary
        List<String> advice)     // zero to three concrete action items
    {
        public WeeklyBrief {
            advice = advice == null ? List.of() : List.copyOf(advice);
        }
    }

    // ---------- model capabilities ----------

    /**
     * Identify the most noteworthy periods in the 7-day forecast: any
     * extremes, rain, wind, or temperature swings that matter for someone
     * in San Rafael, California. Prefer daytime periods over night periods
     * when several are similar. Return the names of at most three periods
     * plus a single-sentence reason.
     */
    @Generate(prompt = """
        Identify the most noteworthy periods in the 7-day forecast: any
        extremes, rain, wind, or temperature swings that matter for someone
        in San Rafael, California. Prefer daytime periods over night periods
        when several are similar. Return the names of at most three periods
        plus a single-sentence reason.
        """)
    @Strategy(PredictStrategy.class)
    public NoteworthyDays noteworthyDays(List<DailyForecast> forecast) {
        throw new UnsupportedOperationException("Generated at runtime");
    }

    /**
     * Write a friendly weekly weather brief for a San Rafael resident.
     * Max 5 sentences. Lead with the single most important takeaway in
     * 'headline'. Include specific temperature highs in °F. Do not
     * mention cities other than San Rafael.
     */
    @Generate(prompt = """
        Write a friendly weekly weather brief for a San Rafael resident.
        Max 5 sentences. Lead with the single most important takeaway in
        'headline'. Include specific temperature highs in °F. Do not
        mention cities other than San Rafael.
        """)
    @Strategy(PredictStrategy.class)
    public WeeklyBrief weeklyBrief(List<DailyForecast> forecast, NoteworthyDays highlights) {
        throw new UnsupportedOperationException("Generated at runtime");
    }

    // ---------- deterministic data collection ----------

    List<DailyForecast> fetchForecast() {
        try {
            // This is deliberately plain Java: the weather service has a
            // documented HTTP contract and does not benefit from a model call.
            // 1. Resolve the gridpoint forecast URL for the coordinates.
            var points = getJson("https://api.weather.gov/points/" + LAT + "," + LON);
            var forecastUrl = points.path("properties").path("forecast").asText();

            // 2. Map the next 14 periods (7 day/night pairs) to typed records.
            var forecast = getJson(forecastUrl);
            var periods = forecast.path("properties").path("periods");
            List<DailyForecast> out = new ArrayList<>();
            for (int i = 0; i < periods.size() && out.size() < MAX_FORECAST_PERIODS; i++) {
                JsonNode p = periods.get(i);
                out.add(new DailyForecast(
                    p.path("name").asText(),
                    p.path("temperature").asInt(),
                    p.path("windSpeed").asText(),
                    p.path("windDirection").asText(),
                    p.path("shortForecast").asText()));
            }
            if (out.isEmpty()) {
                throw new IllegalStateException("NWS returned no forecast periods");
            }
            return out;
        } catch (Exception e) {
            throw new RuntimeException("Weather fetch failed: " + e.getMessage(), e);
        }
    }

    private JsonNode getJson(String url) throws Exception {
        var req = HttpRequest.newBuilder(URI.create(url))
            .header("User-Agent", "nooa-weather-agent demo (contact: developer@example.com)")
            .header("Accept", "application/geo+json")
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build();
        var body = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
        return json.readTree(body);
    }

    // ---------- orchestrator: plain Java owns the workflow ----------

    public WeeklyBrief weeklyBriefForSanRafael() {
        var forecast = fetchForecast();                            // deterministic Java
        var highlights = noteworthyDays(forecast);                 // @Generate: model judgement
        return weeklyBrief(forecast, highlights);                  // @Generate: model response
    }

    public static void main(String[] args) {
        // AgentFactory instruments @Generate methods. Calling the generated
        // subclass, rather than new WeatherAgent(...), is what activates NOOA.
        var llm = ExampleLLM.create();
        try (var agent = AgentFactory.create(WeatherAgent.class, llm)) {
            var brief = agent.weeklyBriefForSanRafael();
            System.out.println("=== " + brief.headline() + " ===");
            System.out.println(brief.body());
            brief.advice().forEach(advice -> System.out.println("- " + advice));
        }
    }
}
