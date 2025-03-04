export interface LocalStorageMigrationPlugin {
  migrateData(): Promise<{ success: boolean }>;
}