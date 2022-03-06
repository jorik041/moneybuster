package net.eneiluj.moneybuster.util;

import net.eneiluj.moneybuster.model.parsed.AustrianBillQrCode;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BillParser {
    private final SimpleDateFormat austrianQrCodeDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT);

    /**
     * Input structure:
     * Separator: _
     * Index: Meaning
     * 1: Algorithm
     * 2: CashDeskId
     * 3: CheckNumber
     * 4: LocalDateTime
     * 5: Amount20%Tax
     * 6: Amount19%Tax
     * 7: Amount13%Tax
     * 8: Amount10%Tax
     * 9: Amount0%Tax
     * 10-13: aditionalMetadata
     *
     * Example of the input:
     * _R1-AT1_rk-01_ft102DF#64551_2022-03-05T11:29:34_0,00_23,25_0,00_0,00_0,00_nxsJ06w=_6f3deaee_2FYzVcRi2NU=_ueezgtX24lRBTBwCpXbHSt7O8I3J5HO9pwYvPUd7tG6o1kPIKUAXCqvs+81DPhMRSqTke8qeVoKw/YNBrGrI9A==
     *
     * @param scannedBill
     * @return
     */
    public AustrianBillQrCode parseAustrianBillFromQrCode(String scannedBill) throws ParseException {
        String[] splitBill = scannedBill.split("_");

        if(splitBill.length < 10) {
            throw new ParseException("Could not parse bill!", 0);
        }

        Date date = austrianQrCodeDateFormat.parse(splitBill[4]);
        double totalAmount = 0;
        for(int i = 1; i <= 5; i++) {
            totalAmount += SupportUtil.commaNumberFormat.parse(splitBill[4+i]).doubleValue();
        }
        // some amounts may be negative that's why we have to round here
        return new AustrianBillQrCode(splitBill[2], date, Math.round(totalAmount*100.0)/100.0);
    }
}
