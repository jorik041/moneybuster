#!/bin/bash

declare -A langCodes

langCodes=(
    ["fr"]="fr-FR" ["de"]="de-DE" ["es"]="es-ES" ["el"]="el-GR" ["it"]="it-IT"
    ["nl"]="nl-NL" ["pl"]="pl-PL" ["ro"]="ro-RO" ["ru"]="ru-RU"
    ["vi"]="vi-VN" ["eo"]="eo-UY" ["pt-BR"]="pt-BR" ["sr"]="sr-SP"
    ["tr"]="tr-TR" ["oc"]="oc-FR" ["fa"]="fa-IR" ["fi"]="fi-FI"
    ["zh-CN"]="zh-CN" ["hu"]="hu-HU" ["ta"]="ta-IN" ["pt-PT"]="pt-PT"
    ["ar"]="ar-SA" ["es-ES"]="es-ES" ["cs"]="cs-CZ" ["th"]="th-TH"
    ["sv-SE"]="sv-SE" ["en-GB"]="en-GB" ["qu"]="qu-PE" ["hi"]="hi-IN"
    ["ca"]="ca-ES" ["sk"]="sk-SK" ["ne-NP"]="ne-NP" ["mn"]="mn-MN"
    ["no"]="no-NO" ["ko"]="ko-KR" ["te"]="te-IN" ["en-US"]="en-US"
    ["sl"]="sl-SI" ["bn"]="bn-BD" ["zh-TW"]="zh-TW" ["bg"]="bg-BG"
    ["af"]="af-ZA" ["uk"]="uk-UA" ["ja"]="ja-JP" ["da"]="da-DK"
    ["he"]="he-IL" ["id"]="id-ID"
)

for path in `find ../app/src/main/res -name "full_description.txt"`; do
    inFolder=`dirname $path`
    inLangFolder=`dirname $inFolder`
    inLang=`basename $inLangFolder`
    outLang=${langCodes[$inLang]}
    outFolder=metadata/android/$outLang

    if [ ! -z "$outLang" ]; then
        test -d $outFolder || mkdir $outFolder
        cp $inFolder/full_description.txt $outFolder/
        cp $inFolder/short_description.txt $outFolder/
        echo 'PhoneTrack' > $outFolder/title.txt
    else
        echo "check $inLang language code!!!"
    fi
done

git add .
