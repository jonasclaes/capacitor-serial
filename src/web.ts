import { WebPlugin } from '@capacitor/core';

import type { SerialPlugin } from './definitions';

export class SerialWeb extends WebPlugin implements SerialPlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}
