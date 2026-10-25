package org.kaleta.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

import java.util.Map;

@Data
@RegisterForReflection
@JsonIgnoreProperties(ignoreUnknown = true)
public class FirebaseInstitution
{
    private String name;
    private boolean enabled;
    private boolean trusted;
    private Rating rating;
    private Map<String, String> aliases;

    @Data
    @RegisterForReflection
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Rating
    {
        private Score institutional_weight;
        private Score media_shock_value;

        @Data
        @RegisterForReflection
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Score
        {
            private String score;
            private String description;
        }
    }
}
