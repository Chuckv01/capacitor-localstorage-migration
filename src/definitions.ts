export interface LocalStorageMigrationPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}
