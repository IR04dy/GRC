import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import './styles/tokens.css';
import './styles/globals.css';
import './styles/shell.css';
import './styles/components.css';
import './styles/breakpoints.css';
import './components/ui/data-table/dataTable.css';
import './components/ui/filter/filter.css';
import './components/ui/recordPicker.css';
import './components/ui/sprint4-extras.css';

import { App } from './App';
import { applyToDom as applyWhiteLabel } from './lib/whiteLabel';

// Apply white-label CSS variables before first paint so the topbar / buttons
// render with the tenant's colours from the very first frame, not after a
// flash of indigo. Idempotent — the WhiteLabelPage re-applies on save.
applyWhiteLabel();

const root = document.getElementById('root');
if (!root) throw new Error('Missing #root element in index.html');

createRoot(root).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
