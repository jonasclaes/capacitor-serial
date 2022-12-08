import { WebPlugin } from '@capacitor/core';

import type {
  // ReadCallback,
  // SerialOptions,
  SerialPlugin,
  SerialResponse,
  // SerialWriteOptions,
  // UsbAttachedDetachedCallback,
} from './definitions';

export class SerialWeb extends WebPlugin implements SerialPlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }

  registerUsbAttachedDetachedCallback(
    // callback: UsbAttachedDetachedCallback,
  ): Promise<string> {
    throw new Error('Method not implemented.');
  }

  devices(): Promise<SerialResponse<any>> {
    throw new Error('Method not implemented.');
  }

  open(
    // options: SerialOptions
  ): Promise<SerialResponse<any>> {
    throw new Error('Method not implemented.');
  }

  close(): Promise<SerialResponse<any>> {
    throw new Error('Method not implemented.');
  }

  read(): Promise<SerialResponse<any>> {
    throw new Error('Method not implemented.');
  }

  write(
    // data: SerialWriteOptions
  ): Promise<SerialResponse<any>> {
    throw new Error('Method not implemented.');
  }

  registerReadCallback(
    // callback: ReadCallback
  ): Promise<string> {
    throw new Error('Method not implemented.');
  }
}
