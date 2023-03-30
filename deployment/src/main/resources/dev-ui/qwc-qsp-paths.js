
import { LitElement, html, css} from 'lit';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';
import '@vaadin/grid';
import '@vaadin/text-field';
import { paths } from 'build-time-data';
import { rootPrefix } from 'build-time-data';


/**
 * This component shows the qsp paths.
 */
export class QwcQspPaths extends LitElement {
    
    static styles = css`
       :host {
          display: flex;
          flex-direction: column;
          gap: 10px;
        }
        .paths-table {
          padding-bottom: 10px;
        }
        code {
          font-size: 85%;
        }
        .path-link {
          color: var(--lumo-primary-text-color);
        }
        `;


    render() {
            return html`
                <vaadin-grid .items="${paths}" class="paths-table" theme="no-border" all-rows-visible>
                    <vaadin-grid-column auto-width
                        header="Path"
                        ${columnBodyRenderer(this._renderPath, [])}
                        resizable>
                    </vaadin-grid-column>
                </vaadin-grid>
                `;
    }
     
    _renderPath(path) {
        return html`
            <a href="${rootPrefix}${path}" target="_blank" class="path-link">${rootPrefix}${path}</a>
        `;
    }
    
}
customElements.define('qwc-qsp-paths', QwcQspPaths);
