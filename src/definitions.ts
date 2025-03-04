export interface LocalStorageMigrationPlugin {
  getLegacyData(): Promise<{ [key: string]: string }>;
}