package info.nightscout.androidaps.plugins.Overview.Dialogs;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.HandlerThread;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;

import com.google.common.base.Joiner;
import com.wdullaer.materialdatetimepicker.date.DatePickerDialog;
import com.wdullaer.materialdatetimepicker.time.RadialPickerLayout;
import com.wdullaer.materialdatetimepicker.time.TimePickerDialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.NumberPicker;
import info.nightscout.utils.SP;
import info.nightscout.utils.SafeParse;
import info.nightscout.utils.ToastUtils;

public class NewCarbsDialog extends DialogFragment implements OnClickListener, DatePickerDialog.OnDateSetListener, TimePickerDialog.OnTimeSetListener, CompoundButton.OnCheckedChangeListener {
    private static Logger log = LoggerFactory.getLogger(NewCarbsDialog.class);

    private NumberPicker editCarbs;

    private TextView dateButton;
    private TextView timeButton;

    private Date initialEventTime;
    private Date eventTime;

    private Button fav1Button;
    private Button fav2Button;
    private Button fav3Button;

    private EditText notesEdit;

    private static final int FAV1_DEFAULT = 5;
    private static final int FAV2_DEFAULT = 10;
    private static final int FAV3_DEFAULT = 20;
    private RadioButton startActivityTTCheckbox;
    private RadioButton startEatingSoonTTCheckbox;
    private RadioButton startHypoTTCheckbox;
    private boolean togglingTT;

    private Integer maxCarbs;

    //one shot guards
    private boolean accepted;
    private boolean okClicked;

    public NewCarbsDialog() {
        HandlerThread mHandlerThread = new HandlerThread(NewCarbsDialog.class.getSimpleName());
        mHandlerThread.start();
    }

    final private TextWatcher textWatcher = new TextWatcher() {
        @Override
        public void afterTextChanged(Editable s) {
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            validateInputs();
        }
    };

    private void validateInputs() {
        Integer carbs = SafeParse.stringToInt(editCarbs.getText());
        if (carbs > maxCarbs) {
            editCarbs.setValue(0d);
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.gs(R.string.carbsconstraintapplied));
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.overview_newcarbs_dialog, container, false);

        view.findViewById(R.id.ok).setOnClickListener(this);
        view.findViewById(R.id.cancel).setOnClickListener(this);

        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        maxCarbs = MainApp.getConstraintChecker().getMaxCarbsAllowed().value();

        editCarbs = view.findViewById(R.id.newcarb_carbsamount);

        editCarbs.setParams(0d, 0d, (double) maxCarbs, 1d, new DecimalFormat("0"), false, textWatcher);

        startActivityTTCheckbox = view.findViewById(R.id.newcarbs_activity_tt);
        startActivityTTCheckbox.setOnCheckedChangeListener(this);
        startEatingSoonTTCheckbox = view.findViewById(R.id.newcarbs_eating_soon_tt);
        startEatingSoonTTCheckbox.setOnCheckedChangeListener(this);
        startHypoTTCheckbox = view.findViewById(R.id.newcarbs_hypo_tt);
        startHypoTTCheckbox.setOnCheckedChangeListener(this);

        dateButton = view.findViewById(R.id.newcarbs_eventdate);
        timeButton = view.findViewById(R.id.newcarb_eventtime);

        initialEventTime = new Date();
        eventTime = new Date(initialEventTime.getTime());
        dateButton.setText(DateUtil.dateString(eventTime));
        timeButton.setText(DateUtil.timeString(eventTime));
        dateButton.setOnClickListener(this);
        timeButton.setOnClickListener(this);

        fav1Button = view.findViewById(R.id.newcarbs_plus1);
        fav1Button.setOnClickListener(this);
        fav1Button.setText(toSignedString(SP.getInt(R.string.key_carbs_button_increment_1, FAV1_DEFAULT)));

        fav2Button = view.findViewById(R.id.newcarbs_plus2);
        fav2Button.setOnClickListener(this);
        fav2Button.setText(toSignedString(SP.getInt(R.string.key_carbs_button_increment_2, FAV2_DEFAULT)));

        fav3Button = view.findViewById(R.id.newcarbs_plus3);
        fav3Button.setOnClickListener(this);
        fav3Button.setText(toSignedString(SP.getInt(R.string.key_carbs_button_increment_3, FAV3_DEFAULT)));

        notesEdit = (EditText) view.findViewById(R.id.newcarbs_notes);

        setCancelable(true);
        getDialog().setCanceledOnTouchOutside(false);
        return view;
    }

    private String toSignedString(int value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }

    @Override
    public synchronized void onClick(View view) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(eventTime);
        switch (view.getId()) {
            case R.id.ok:
                submit();
                break;
            case R.id.cancel:
                dismiss();
                break;
            case R.id.newcarbs_eventdate:
                DatePickerDialog dpd = DatePickerDialog.newInstance(
                        this,
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                );
                dpd.setThemeDark(true);
                dpd.dismissOnPause(true);
                dpd.show(getActivity().getFragmentManager(), "Datepickerdialog");
                break;
            case R.id.newcarb_eventtime:
                TimePickerDialog tpd = TimePickerDialog.newInstance(
                        this,
                        calendar.get(Calendar.HOUR_OF_DAY),
                        calendar.get(Calendar.MINUTE),
                        DateFormat.is24HourFormat(getActivity())
                );
                tpd.setThemeDark(true);
                tpd.dismissOnPause(true);
                tpd.show(getActivity().getFragmentManager(), "Timepickerdialog");
                break;
            case R.id.newcarbs_plus1:
                editCarbs.setValue(Math.max(0, editCarbs.getValue()
                        + SP.getInt(R.string.key_carbs_button_increment_1, FAV1_DEFAULT)));
                validateInputs();
                break;
            case R.id.newcarbs_plus2:
                editCarbs.setValue(Math.max(0, editCarbs.getValue()
                        + SP.getInt(R.string.key_carbs_button_increment_2, FAV2_DEFAULT)));
                validateInputs();
                break;
            case R.id.newcarbs_plus3:
                editCarbs.setValue(Math.max(0, editCarbs.getValue()
                        + SP.getInt(R.string.key_carbs_button_increment_3, FAV3_DEFAULT)));
                validateInputs();
                break;
            case R.id.newcarbs_activity_tt:
                if (togglingTT) {
                    togglingTT = false;
                    break;
                }
                startActivityTTCheckbox.setOnClickListener(null);
                startActivityTTCheckbox.setOnCheckedChangeListener(null);
                startActivityTTCheckbox.setChecked(false);
                startActivityTTCheckbox.setOnCheckedChangeListener(this);
                break;
            case R.id.newcarbs_eating_soon_tt:
                if (togglingTT) {
                    togglingTT = false;
                    break;
                }
                startEatingSoonTTCheckbox.setOnClickListener(null);
                startEatingSoonTTCheckbox.setOnCheckedChangeListener(null);
                startEatingSoonTTCheckbox.setChecked(false);
                startEatingSoonTTCheckbox.setOnCheckedChangeListener(this);
                break;
            case R.id.newcarbs_hypo_tt:
                if (togglingTT) {
                    togglingTT = false;
                    break;
                }
                startHypoTTCheckbox.setOnClickListener(null);
                startHypoTTCheckbox.setOnCheckedChangeListener(null);
                startHypoTTCheckbox.setChecked(false);
                startHypoTTCheckbox.setOnCheckedChangeListener(this);
                break;
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        // Logic to disable a selected radio when pressed. When a checked radio
        // is pressed, no CheckChanged event is trigger, so register a Click event
        // when checking a radio. Since Click events come after CheckChanged events,
        // the Click event is triggered immediately after this. Thus, set toggingTT
        // var to true, so that the first Click event fired after this is ignored.
        // Radios remove themselves from Click events once unchecked.
        // Since radios are not in a group, manually update their state.
        switch (buttonView.getId()) {
            case R.id.newcarbs_activity_tt:
                togglingTT = true;
                startActivityTTCheckbox.setOnClickListener(this);

                startEatingSoonTTCheckbox.setOnCheckedChangeListener(null);
                startEatingSoonTTCheckbox.setChecked(false);
                startEatingSoonTTCheckbox.setOnCheckedChangeListener(this);

                startHypoTTCheckbox.setOnCheckedChangeListener(null);
                startHypoTTCheckbox.setChecked(false);
                startHypoTTCheckbox.setOnCheckedChangeListener(this);
                break;
            case R.id.newcarbs_eating_soon_tt:
                togglingTT = true;
                startEatingSoonTTCheckbox.setOnClickListener(this);

                startActivityTTCheckbox.setOnCheckedChangeListener(null);
                startActivityTTCheckbox.setChecked(false);
                startActivityTTCheckbox.setOnCheckedChangeListener(this);

                startHypoTTCheckbox.setOnCheckedChangeListener(null);
                startHypoTTCheckbox.setChecked(false);
                startHypoTTCheckbox.setOnCheckedChangeListener(this);
                break;
            case R.id.newcarbs_hypo_tt:
                togglingTT = true;
                startHypoTTCheckbox.setOnClickListener(this);

                startActivityTTCheckbox.setOnCheckedChangeListener(null);
                startActivityTTCheckbox.setChecked(false);
                startActivityTTCheckbox.setOnCheckedChangeListener(this);

                startEatingSoonTTCheckbox.setOnCheckedChangeListener(null);
                startEatingSoonTTCheckbox.setChecked(false);
                startEatingSoonTTCheckbox.setOnCheckedChangeListener(this);
                break;
        }
    }

    private void submit() {
        if (okClicked) {
            log.debug("guarding: ok already clicked");
            dismiss();
            return;
        }
        okClicked = true;
        try {
            final Integer carbs = SafeParse.stringToInt(editCarbs.getText());
            Integer carbsAfterConstraints = MainApp.getConstraintChecker().applyCarbsConstraints(new Constraint<>(carbs)).value();

            List<String> actions = new LinkedList<>();
            if (carbs > 0)
                actions.add(MainApp.gs(R.string.carbs) + ": " + "<font color='" + MainApp.gc(R.color.colorCarbsButton) + "'>" + carbsAfterConstraints + "g" + "</font>");
            if (!carbsAfterConstraints.equals(carbs))
                actions.add("<font color='" + MainApp.gc(R.color.low) + "'>" + MainApp.gs(R.string.carbsconstraintapplied) + "</font>");

            final Profile currentProfile = MainApp.getConfigBuilder().getProfile();
            if (currentProfile == null)
                return;

            int activityTTDuration = SP.getInt(R.string.key_activity_duration, Constants.defaultActivityTTDuration);
            activityTTDuration = activityTTDuration > 0 ? activityTTDuration : Constants.defaultActivityTTDuration;
            double activityTT = SP.getDouble(R.string.key_activity_target, currentProfile.getUnits().equals(Constants.MMOL) ? Constants.defaultActivityTTmmol : Constants.defaultActivityTTmgdl);
            activityTT = activityTT > 0 ? activityTT : currentProfile.getUnits().equals(Constants.MMOL) ? Constants.defaultActivityTTmmol : Constants.defaultActivityTTmgdl;

            int eatingSoonTTDuration = SP.getInt(R.string.key_eatingsoon_duration, Constants.defaultEatingSoonTTDuration);
            eatingSoonTTDuration = eatingSoonTTDuration > 0 ? eatingSoonTTDuration : Constants.defaultEatingSoonTTDuration;
            double eatingSoonTT = SP.getDouble(R.string.key_eatingsoon_target, currentProfile.getUnits().equals(Constants.MMOL) ? Constants.defaultEatingSoonTTmmol : Constants.defaultEatingSoonTTmgdl);
            eatingSoonTT = eatingSoonTT > 0 ? eatingSoonTT : currentProfile.getUnits().equals(Constants.MMOL) ? Constants.defaultEatingSoonTTmmol : Constants.defaultEatingSoonTTmgdl;

            int hypoTTDuration = SP.getInt(R.string.key_hypo_duration, Constants.defaultHypoTTDuration);
            hypoTTDuration = hypoTTDuration > 0 ? hypoTTDuration : Constants.defaultHypoTTDuration;
            double hypoTT = SP.getDouble(R.string.key_hypo_target, currentProfile.getUnits().equals(Constants.MMOL) ? Constants.defaultHypoTTmmol : Constants.defaultHypoTTmgdl);
            hypoTT = hypoTT > 0 ? hypoTT : currentProfile.getUnits().equals(Constants.MMOL) ? Constants.defaultHypoTTmmol : Constants.defaultHypoTTmgdl;

            if (startActivityTTCheckbox.isChecked()) {
                if (currentProfile.getUnits().equals(Constants.MMOL)) {
                    actions.add(MainApp.gs(R.string.temptargetshort) + ": " + "<font color='" + MainApp.gc(R.color.high) + "'>" + DecimalFormatter.to1Decimal(activityTT) + " mmol/l (" + activityTTDuration + " min)</font>");
                } else
                    actions.add(MainApp.gs(R.string.temptargetshort) + ": " + "<font color='" + MainApp.gc(R.color.high) + "'>" + DecimalFormatter.to0Decimal(activityTT) + " mg/dl (" + activityTTDuration + " min)</font>");

            }
            if (startEatingSoonTTCheckbox.isChecked()) {
                if (currentProfile.getUnits().equals(Constants.MMOL)) {
                    actions.add(MainApp.gs(R.string.temptargetshort) + ": " + "<font color='" + MainApp.gc(R.color.high) + "'>" + DecimalFormatter.to1Decimal(eatingSoonTT) + " mmol/l (" + eatingSoonTTDuration + " min)</font>");
                } else
                    actions.add(MainApp.gs(R.string.temptargetshort) + ": " + "<font color='" + MainApp.gc(R.color.high) + "'>" + DecimalFormatter.to0Decimal(eatingSoonTT) + " mg/dl (" + eatingSoonTTDuration + " min)</font>");
            }
            if (startHypoTTCheckbox.isChecked()) {
                if (currentProfile.getUnits().equals(Constants.MMOL)) {
                    actions.add(MainApp.gs(R.string.temptargetshort) + ": " + "<font color='" + MainApp.gc(R.color.high) + "'>" + DecimalFormatter.to1Decimal(hypoTT) + " mmol/l (" + hypoTTDuration + " min)</font>");
                } else
                    actions.add(MainApp.gs(R.string.temptargetshort) + ": " + "<font color='" + MainApp.gc(R.color.high) + "'>" + DecimalFormatter.to0Decimal(hypoTT) + " mg/dl (" + hypoTTDuration + " min)</font>");
            }

            final double finalActivityTT = activityTT;
            final int finalActivityTTDuration = activityTTDuration;
            final double finalEatigSoonTT = eatingSoonTT;
            final int finalEatingSoonTTDuration = eatingSoonTTDuration;
            final double finalHypoTT = hypoTT;
            final int finalHypoTTDuration = hypoTTDuration;
            final String finalNotes = notesEdit.getText().toString();

            if (!initialEventTime.equals(eventTime)) {
                actions.add("Time: " + DateUtil.dateAndTimeString(eventTime));
            }

            final int finalCarbsAfterConstraints = carbsAfterConstraints;

            final Context context = getContext();
            final AlertDialog.Builder builder = new AlertDialog.Builder(context);

            builder.setTitle(MainApp.gs(R.string.confirmation));
            builder.setMessage(actions.isEmpty()
                    ? MainApp.gs(R.string.no_action_selected)
                    : Html.fromHtml(Joiner.on("<br/>").join(actions)));
            builder.setPositiveButton(MainApp.gs(R.string.ok), actions.isEmpty() ? null : (dialog, id) -> {
                synchronized (builder) {
                    if (accepted) {
                        log.debug("guarding: already accepted");
                        return;
                    }
                    accepted = true;

                    if (startActivityTTCheckbox.isChecked()) {
                        TempTarget tempTarget = new TempTarget()
                                .date(System.currentTimeMillis())
                                .duration(finalActivityTTDuration)
                                .reason(MainApp.gs(R.string.activity))
                                .source(Source.USER)
                                .low(Profile.toMgdl(finalActivityTT, currentProfile.getUnits()))
                                .high(Profile.toMgdl(finalActivityTT, currentProfile.getUnits()));
                        TreatmentsPlugin.getPlugin().addToHistoryTempTarget(tempTarget);
                    } else if (startEatingSoonTTCheckbox.isChecked()) {
                        TempTarget tempTarget = new TempTarget()
                                .date(System.currentTimeMillis())
                                .duration(finalEatingSoonTTDuration)
                                .reason(MainApp.gs(R.string.eatingsoon))
                                .source(Source.USER)
                                .low(Profile.toMgdl(finalEatigSoonTT, currentProfile.getUnits()))
                                .high(Profile.toMgdl(finalEatigSoonTT, currentProfile.getUnits()));
                        TreatmentsPlugin.getPlugin().addToHistoryTempTarget(tempTarget);
                    } else if (startHypoTTCheckbox.isChecked()) {
                        TempTarget tempTarget = new TempTarget()
                                .date(System.currentTimeMillis())
                                .duration(finalHypoTTDuration)
                                .reason(MainApp.gs(R.string.hypo))
                                .source(Source.USER)
                                .low(Profile.toMgdl(finalHypoTT, currentProfile.getUnits()))
                                .high(Profile.toMgdl(finalHypoTT, currentProfile.getUnits()));
                        TreatmentsPlugin.getPlugin().addToHistoryTempTarget(tempTarget);
                    }

                    if (finalCarbsAfterConstraints > 0) {
                        DetailedBolusInfo detailedBolusInfo = new DetailedBolusInfo();
                        detailedBolusInfo.date = eventTime.getTime();
                        detailedBolusInfo.eventType = CareportalEvent.CARBCORRECTION;
                        detailedBolusInfo.carbs = finalCarbsAfterConstraints;
                        detailedBolusInfo.context = context;
                        detailedBolusInfo.source = Source.USER;
                        detailedBolusInfo.notes = finalNotes;
                        if (ConfigBuilderPlugin.getActivePump().getPumpDescription().storesCarbInfo) {
                            ConfigBuilderPlugin.getCommandQueue().bolus(detailedBolusInfo, new Callback() {
                                @Override
                                public void run() {
                                    if (!result.success) {
                                        Intent i = new Intent(MainApp.instance(), ErrorHelperActivity.class);
                                        i.putExtra("soundid", R.raw.boluserror);
                                        i.putExtra("status", result.comment);
                                        i.putExtra("title", MainApp.gs(R.string.treatmentdeliveryerror));
                                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                        MainApp.instance().startActivity(i);
                                    }
                                }
                            });
                        } else {
                            TreatmentsPlugin.getPlugin().addToHistoryTreatment(detailedBolusInfo);
                        }
                    }
                }
            });
            builder.setNegativeButton(MainApp.gs(R.string.cancel), null);
            builder.show();
            dismiss();
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }
    }

    @Override
    public void onDateSet(DatePickerDialog view, int year, int monthOfYear, int dayOfMonth) {
        eventTime.setYear(year - 1900);
        eventTime.setMonth(monthOfYear);
        eventTime.setDate(dayOfMonth);
        dateButton.setText(DateUtil.dateString(eventTime));
    }

    @Override
    public void onTimeSet(RadialPickerLayout view, int hourOfDay, int minute, int second) {
        eventTime.setHours(hourOfDay);
        eventTime.setMinutes(minute);
        eventTime.setSeconds(second);
        timeButton.setText(DateUtil.timeString(eventTime));
    }
}
