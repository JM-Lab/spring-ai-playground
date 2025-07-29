package jm.kr.spring.ai.playground.webui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.DomEvent;
import com.vaadin.flow.component.EventData;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.HasStyle;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.function.SerializableConsumer;
import com.vaadin.flow.shared.Registration;

@Tag("json-editor-wrapper")
@NpmPackage(value = "jsoneditor", version = "10.2.0")
@NpmPackage(value = "ace-builds", version = "1.43.2")
@JsModule("./playground/json-editor-wrapper.js")
public class JsonEditorWrapper extends Component implements HasSize, HasStyle {

    public JsonEditorWrapper() {
        setEditorMode("code");
    }

    public void setEditorMode(String mode) {
        getElement().setProperty("mode", mode);
    }

    public void setJson(String json) {
        getElement().setProperty("json", json);
    }

    public String getJsonSync() {
        return getElement().getProperty("json");
    }

    public void fetchJson(SerializableConsumer<String> callback) {
        getElement().callJsFunction("getJson").then(String.class, callback);
    }

    @DomEvent("json-change")
    public static class JsonChangeEvent extends ComponentEvent<JsonEditorWrapper> {
        private final String json;

        public JsonChangeEvent(JsonEditorWrapper source, boolean fromClient,
                @EventData("event.detail.json") String json) {
            super(source, fromClient);
            this.json = json;
        }

        public String getJson() {
            return json;
        }
    }

    public Registration addJsonChangeListener(ComponentEventListener<JsonChangeEvent> listener) {
        return addListener(JsonChangeEvent.class, listener);
    }
}