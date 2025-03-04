import { WebPlugin } from '@capacitor/core';

import type { LocalStorageMigrationPlugin } from './definitions';

export class LocalStorageMigrationWeb extends WebPlugin implements LocalStorageMigrationPlugin {
  async getLegacyData(): Promise<{ [key: string]: string }> {
    // In web context, just return current localStorage
    const data: { [key: string]: string } = {};
    
    return data;
  }
}