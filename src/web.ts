import { WebPlugin } from '@capacitor/core';

import type { LocalStorageMigrationPlugin } from './definitions';

export class LocalStorageMigrationWeb extends WebPlugin implements LocalStorageMigrationPlugin {
  async migrateData(): Promise<{ success: boolean }> {
    // Web doesn't need migration since localStorage is already available
    return { success: true };
  }
}