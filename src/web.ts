import { WebPlugin } from '@capacitor/core';

import type { LocalStorageMigrationPlugin } from './definitions';

export class LocalStorageMigrationWeb extends WebPlugin implements LocalStorageMigrationPlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}
