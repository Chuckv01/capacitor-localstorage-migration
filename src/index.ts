import { registerPlugin } from '@capacitor/core';

import type { LocalStorageMigrationPlugin } from './definitions';

const LocalStorageMigration = registerPlugin<LocalStorageMigrationPlugin>('LocalStorageMigration', {
  web: () => import('./web').then((m) => new m.LocalStorageMigrationWeb()),
});

export * from './definitions';
export { LocalStorageMigration };
