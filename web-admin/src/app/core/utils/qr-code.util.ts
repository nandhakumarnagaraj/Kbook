import QRCode from 'qrcode';

export interface UpiQrOptions {
  vpa: string;
  payeeName: string;
  amount?: number;
  note?: string;
  size?: number;
}

/**
 * Builds standard NPCI UPI URI:
 * upi://pay?pa={vpa}&pn={payeeName}&am={amount}&cu=INR&tn={note}
 */
export function buildUpiUri(options: UpiQrOptions): string {
  const params = new URLSearchParams();
  params.set('pa', options.vpa.trim());
  params.set('pn', options.payeeName.trim() || 'Merchant');
  if (options.amount !== undefined && options.amount > 0) {
    params.set('am', options.amount.toFixed(2));
  }
  params.set('cu', 'INR');
  if (options.note) {
    params.set('tn', options.note.trim());
  }
  return `upi://pay?${params.toString()}`;
}

/**
 * Generates a PNG Data URL representing the UPI QR code.
 */
export async function generateUpiQrDataUrl(options: UpiQrOptions): Promise<string> {
  const uri = buildUpiUri(options);
  return QRCode.toDataURL(uri, {
    width: options.size || 256,
    margin: 2,
    color: {
      dark: '#111827',
      light: '#ffffff'
    },
    errorCorrectionLevel: 'H'
  });
}
