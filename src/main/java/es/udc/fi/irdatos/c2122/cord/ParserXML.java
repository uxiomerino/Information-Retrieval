package es.udc.fi.irdatos.c2122.cord;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;  

@JsonIgnoreProperties(ignoreUnknown = true)
public record ParserXML(
        @JacksonXmlProperty(isAttribute = true, localName = "number") String number,
        @JacksonXmlProperty(localName = "query") String query) {}