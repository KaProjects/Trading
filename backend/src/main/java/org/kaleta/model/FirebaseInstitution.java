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
    private Map<String, String> aliases;
}
