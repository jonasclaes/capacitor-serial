export type CallbackID = string;

export type UsbAttachedDetachedCallback = (data: SerialResponse<any>) => void;

export type ReadCallback = (data: SerialResponse<any>) => void;

export interface SerialOptions {
  deviceId: number;
  portNum: number;
  baudRate?: number;
  dataBits?: number;
  stopBits?: number;
  parity?: number;
  dtr?: boolean;
  rts?: boolean;
  sleepOnPause?: boolean;
}

export interface SerialWriteOptions {
  data: string;
}

export interface SerialError {
  message: string;
  cause: string;
}

export interface SerialResponse<T> {
  success: boolean;
  error?: SerialError;
  data?: T;
}

export interface SerialPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
  registerUsbAttachedDetachedCallback(
    callback: UsbAttachedDetachedCallback,
  ): Promise<CallbackID>;
  devices(): Promise<SerialResponse<any>>;
  open(options: SerialOptions): Promise<SerialResponse<any>>;
  close(): Promise<SerialResponse<any>>;
  read(): Promise<SerialResponse<any>>;
  write(data: SerialWriteOptions): Promise<SerialResponse<any>>;
  registerReadCallback(callback: ReadCallback): Promise<CallbackID>;
}
