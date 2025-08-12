// Sample TypeScript file with various import statements for testing
// noinspection ES6UnusedImports,TypeScriptCheckImport,JSUnusedLocalSymbols,JSUnusedGlobalSymbols

// Default import
import React from 'react';

// Named imports
import { useState, useEffect } from 'react';

// Aliased imports
import { Component as ReactComponent } from 'react';
import * as Utils from './utils';

// Mixed imports
import DefaultExport, { namedExport } from './module';

// Side effect import
import './styles.css';

// Dynamic import (for completeness, though it's not a static import)
// const module = await import('./dynamic-module');

// Re-export
export { publicFunction } from './internal-module';
export * from './all-exports';

// Local function using imports
function MyComponent() {
    const [count, setCount] = useState(0);

    useEffect(() => {
        console.log('Component mounted');
    }, []);

    return React.createElement('div', null, `Count: ${count}`);
}
