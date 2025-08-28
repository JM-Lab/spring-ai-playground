import JSONEditor from 'jsoneditor/dist/jsoneditor.min.js';
import 'jsoneditor/dist/jsoneditor.min.css';
import 'ace-builds/src-noconflict/ace';
import 'ace-builds/src-noconflict/mode-json';
import 'ace-builds/src-noconflict/worker-json';
import 'ace-builds/src-noconflict/ext-searchbox';
import 'ace-builds/src-noconflict/theme-textmate';
import 'ace-builds/esm-resolver';
import {config as aceConfig} from 'ace-builds';

const jsonWorkerUrl = new URL('ace-builds/src-noconflict/worker-json.js', import.meta.url);
aceConfig.setModuleUrl('ace/mode/json_worker', jsonWorkerUrl.toString());

class JsonEditorWc extends HTMLElement {
    static get observedAttributes() { return ['json', 'mode']; }

    constructor() {
        super();
        this.handleChange = this.handleChange.bind(this);
    }

    connectedCallback() {
        const container = document.createElement('div');
        container.style.width = '100%';
        container.style.height = '100%';
        this.appendChild(container);

        const options = {
            mode: this.getAttribute('mode') || 'tree',
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

        const initialJson = this.getAttribute('json');
        if (initialJson != null) {
            this._setJsonToEditor(initialJson);
        }
    }

    attributeChangedCallback(name, oldValue, newValue) {
        if (oldValue === newValue) return;
        if (!this.editor) return;

        if (name === 'mode') {
            this.editor.setMode(newValue);
        } else if (name === 'json') {
            this._setJsonToEditor(newValue);
        }
    }

    handleChange() {
        const currentJson = this._getEditorValue();
        if (currentJson === this.getAttribute('json')) return;
        this.setAttribute('json', currentJson);
        this.dispatchEvent(new CustomEvent('json-change', {
            bubbles: true,
            composed: true,
            detail: { json: currentJson }
        }));
    }

    get mode() { return this.getAttribute('mode'); }
    set mode(value) {
        if (this.getAttribute('mode') === value) return;
        this.setAttribute('mode', value);
        if (this.editor) this.editor.setMode(value);
    }

    get json() { return this.getAttribute('json'); }
    set json(value) {
        if (this.getAttribute('json') === value) return;
        this.setAttribute('json', value);
        this._setJsonToEditor(value);
    }

    _setJsonToEditor(value) {
        if (!this.editor) return;
        const current = this._getEditorValue();
        if (value === current) return;
        try {
            this.editor.set(JSON.parse(value));
        } catch (e) {
            this.editor.setText(value || '{}');
        }
    }

    getJson() {
        return this._getEditorValue();
    }

    _getEditorValue() {
        if (!this.editor) return this.json || '{}';
        try {
            return JSON.stringify(this.editor.get(), null, 2);
        } catch (e) {
            return this.editor.getText();
        }
    }
}

customElements.define('json-editor-wrapper', JsonEditorWc);
