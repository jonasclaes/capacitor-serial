export interface SerialPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}
