import { buildUpiUri, generateUpiQrDataUrl } from './qr-code.util';

describe('QrCodeUtil', () => {
  it('should build a standard NPCI UPI URI', () => {
    const uri = buildUpiUri({
      vpa: 'nandhakumar2536-1@oksbi',
      payeeName: 'KhanaBook Cafe',
      amount: 1.00,
      note: 'Verification'
    });
    expect(uri).toContain('upi://pay?');
    expect(uri).toContain('pa=nandhakumar2536-1%40oksbi');
    expect(uri).toContain('pn=KhanaBook+Cafe');
    expect(uri).toContain('am=1.00');
    expect(uri).toContain('cu=INR');
    expect(uri).toContain('tn=Verification');
  });

  it('should generate a base64 PNG data URL', async () => {
    const dataUrl = await generateUpiQrDataUrl({
      vpa: 'test@upi',
      payeeName: 'Test Store'
    });
    expect(dataUrl.startsWith('data:image/png;base64,')).toBeTrue();
  });
});
