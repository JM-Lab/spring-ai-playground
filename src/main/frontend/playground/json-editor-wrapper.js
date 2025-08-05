import JSONEditor from 'jsoneditor/dist/jsoneditor.min.js';
import 'jsoneditor/dist/jsoneditor.min.css';
import 'ace-builds/src-noconflict/ace';

class JsonEditorWc extends HTMLElement {

    static get observedAttributes() { return ['json', 'mode']; }

    constructor() {
        super();
        this.handleChange = this.handleChange.bind(this);
    }

    connectedCallback() {
        const container = document.createElement('div');
        container.style.width  = '100%';
        container.style.height = '100%';
        this.appendChild(container);

        const options = {
            mode: this.mode || 'tree',
            history: true,
            enableSort: false,
            enableTransform: false,
            navigationBar: false,
            statusBar: true,
            mainMenuBar: true,
            search: false,
            onChange: this.handleChange
        };

        this.editor = new JSONEditor(container, options);

        if (this.json) {
            this._setJsonToEditor(this.json);
        }
    }

    attributeChangedCallback(name, _oldValue, newValue) {
        if (name === 'mode') {
            this.mode = newValue;
        }
        if (name === 'json') {
            this.json = newValue;
        }
    }

    handleChange() {
        const currentJson = this._getEditorValue();
        if (currentJson === this.getAttribute('json')) {
            return;
        }
        this.setAttribute('json', currentJson);
        const event = new CustomEvent('json-change', {
            bubbles: true,
            composed: true,
            detail: { json: currentJson }
        });
        this.dispatchEvent(event);
    }

    get mode() {
        return this.getAttribute('mode');
    }

    set mode(value) {
        this.setAttribute('mode', value);
        if (this.editor) {
            this.editor.setMode(value);
        }
    }

    get json() {
        return this.getAttribute('json');
    }

    set json(value) {
        this.setAttribute('json', value);
        this._setJsonToEditor(value);
    }

    _setJsonToEditor(value) {
        if (this.editor && value !== this._getEditorValue()) {
            try {
                this.editor.set(JSON.parse(value));
            } catch (e) {
                this.editor.setText(value || '{}');
            }
        }
    }

    getJson() {
        return this._getEditorValue();
    }

    _getEditorValue() {
        if (!this.editor) {
            return this.json || '{}';
        }
        try {
            return JSON.stringify(this.editor.get(), null, 2);
        } catch (e) {
            return this.editor.getText();
        }
    }
}

customElements.define('json-editor-wrapper', JsonEditorWc);
