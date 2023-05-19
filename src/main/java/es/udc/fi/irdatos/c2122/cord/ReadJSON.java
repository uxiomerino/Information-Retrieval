package es.udc.fi.irdatos.c2122.cord;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ReadJSON(List<BodyText> body_text) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static record BodyText(String text, String section) {
    }
}
