package net.eneiluj.moneybuster.android.activity;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import net.eneiluj.moneybuster.R;
import net.eneiluj.moneybuster.model.DBCurrency;
import net.eneiluj.moneybuster.persistence.MoneyBusterSQLiteOpenHelper;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputLayout;

import java.util.List;

public class ManageCurrenciesActivity extends AppCompatActivity {
    private MoneyBusterSQLiteOpenHelper db = null;

    private TextView mainCurrencyWarningTextView;
    private EditText mainCurrencyTextEdit;
    private EditText newCurrencyRateTextEdit;
    private TextInputLayout newCurrencyRateLayout;
    private EditText newCurrencyNameTextEdit;
    private Button buttonSaveMainCurrency;
    private Button buttonAddCurrency;
    private LinearLayout currenciesTable;

    private long selectedProjectID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_currencies);

        mainCurrencyTextEdit = findViewById(R.id.editTextMainCurrencyName);
        newCurrencyNameTextEdit = findViewById(R.id.add_currency_name);
        newCurrencyRateTextEdit = findViewById(R.id.add_currency_rate);
        newCurrencyRateLayout = findViewById(R.id.add_currency_rate_layout);
        buttonSaveMainCurrency = findViewById(R.id.buttonsavemaincurrency);
        buttonAddCurrency = findViewById(R.id.add_currency_btn);
        mainCurrencyWarningTextView = findViewById(R.id.textViewMainCurrencyWarning);
        currenciesTable = findViewById(R.id.currencies_table);

        db = MoneyBusterSQLiteOpenHelper.getInstance(this);
        selectedProjectID = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getLong("selected_project", 0);
        String mainCurrency = db.getProject(selectedProjectID).getCurrencyName();

        mainCurrencyTextEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                checkMainCurrencyTextEdit();
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
        buttonSaveMainCurrency.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String newMaincurrencyName = mainCurrencyTextEdit.getText().toString();
                mainCurrencyTextEdit.clearFocus();
                db.updateProject(selectedProjectID, null, null, null, null, null, newMaincurrencyName);
            }
        });

        buttonAddCurrency.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(newCurrencyNameTextEdit.getText().toString().length() > 0 && newCurrencyRateTextEdit.getText().toString().length() > 0){
                    double exchangeRate = Double.parseDouble(newCurrencyRateTextEdit.getText().toString());
                    DBCurrency newCurrency= new DBCurrency(0,
                            0,
                            selectedProjectID,
                            newCurrencyNameTextEdit.getText().toString(), exchangeRate
                            );
                    db.addCurrency(newCurrency);
                    newCurrencyNameTextEdit.setText("");
                    newCurrencyRateTextEdit.setText("");
                    newCurrencyNameTextEdit.clearFocus();
                    newCurrencyRateTextEdit.clearFocus();
                    updateCurrenciesList();
                }
            }
        });

        newCurrencyNameTextEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                checkNewCurrencyCanBeAdded();
                updateRateHint();
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        newCurrencyRateTextEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                checkNewCurrencyCanBeAdded();
                updateRateHint();
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        if(mainCurrency == null){
            buttonSaveMainCurrency.setEnabled(false);
        }else{
            mainCurrencyTextEdit.setText(mainCurrency);
            mainCurrencyWarningTextView.setVisibility(View.GONE);
            updateCurrenciesList();
        }
    }

    private void checkMainCurrencyTextEdit(){
        if(mainCurrencyTextEdit.getText().toString().length() == 0){
            buttonSaveMainCurrency.setEnabled(false);
        }else{
            buttonSaveMainCurrency.setEnabled(true);
        }
    }

    private void updateRateHint(){
        String updatedMainCurrency = db.getProject(selectedProjectID).getCurrencyName();
        String rateValue = newCurrencyRateTextEdit.getText().toString();
        if(rateValue.length() == 0){
            rateValue = "X";
        }
        if(newCurrencyNameTextEdit.getText().toString().length() > 0 && updatedMainCurrency != null){
            newCurrencyRateLayout.setHint("1 " + newCurrencyNameTextEdit.getText().toString() + " = " + rateValue + " " + updatedMainCurrency);
            newCurrencyRateLayout.setEnabled(true);
        }else{
            newCurrencyRateLayout.setHint(getString(R.string.currency_rate));
            newCurrencyRateLayout.setEnabled(false);
        }
    }

    private void checkNewCurrencyCanBeAdded() {
        if (newCurrencyNameTextEdit.getText().toString().length() > 0 && newCurrencyRateTextEdit.getText().toString().length() > 0) {
            buttonAddCurrency.setEnabled(true);
        }else{
            buttonAddCurrency.setEnabled(false);
        }
    }

    private void updateCurrenciesList(){
        currenciesTable.removeAllViews();
        List<DBCurrency> currenciesDB = db.getCurrencies(selectedProjectID);
        for(DBCurrency currency: currenciesDB) {
            View row = LayoutInflater.from(getApplicationContext()).inflate(R.layout.currency_row, null);
            TextView curr_name = row.findViewById(R.id.curr_name);
            //curr_name.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default));
            curr_name.setText(currency.getName());

            TextView curr_rate = row.findViewById(R.id.curr_rate);
            //curr_rate.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default));
            curr_rate.setText(String.valueOf(currency.getExchangeRate()));

            Button button_delete = row.findViewById(R.id.delete_currency_btn);
            button_delete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    db.deleteCurrency(currency.getId());
                    updateCurrenciesList();
                }
            });

            currenciesTable.addView(row);

        }
    }
}
