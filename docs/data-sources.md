# Data sources

SkyCanvas uses the [Open-Meteo Forecast API](https://open-meteo.com/en/docs). The repository includes six unmodified JSON responses: four from the first release and two recorded for version 2. Each file's exact URL, UTC retrieval time and SHA-256 digest are recorded in `data/provenance.json`. The same bytes are bundled in the Android assets and embedded in the offline companion.

| Place | Requested latitude | Requested longitude | Display time zone |
| --- | ---: | ---: | --- |
| Isfahan | 32.6546 | 51.6680 | Asia/Tehran |
| Shiraz | 29.5918 | 52.5837 | Asia/Tehran |
| Tehran | 35.6892 | 51.3890 | Asia/Tehran |
| Tabriz | 38.0800 | 46.2919 | Asia/Tehran |

These are public model forecasts. A grid cell can differ slightly from the requested coordinates. Current conditions are model-derived, rather than a direct reading from a nearby sensor. The bundled records are a reproducible view of the response at retrieval time; they do not claim to describe the weather when someone opens the demo later.

## Interpretation

- Temperature and apparent temperature are degrees Celsius in storage. Fahrenheit is a display preference.
- Wind speed is kilometres per hour. Relative humidity and precipitation probability are percentages.
- Hourly precipitation is millimetres accumulated during the preceding hour. A zero amount and a non-zero probability can coexist.
- The daily weather code is the day's most severe condition. The seven-day view does not imply that condition lasts all day.
- `timeformat=unixtime` provides UTC epoch seconds. The UI converts instants using the supplied IANA time zone.
- Missing values remain unavailable in labels and leave gaps in a chart. Unknown codes do not become a fabricated sunny forecast.

The scene is an interpretation of a condition code. Its bridge, river, buildings and colors are an illustration, not a geographic map or a representation of present river levels. Studio's six weather states are visual studies and do not add fabricated forecasts to the dataset.

## Attribution and terms

**Weather data by Open-Meteo.** Data is provided under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). The raw records are unchanged; the UI converts units, chooses ranges, rounds labels and draws charts from them. Those presentation changes are SkyCanvas's responsibility.

The [API service terms](https://open-meteo.com/en/terms) are separate from the data licence. The free endpoint is for non-commercial use within the provider's limits. A commercial deployment needs the appropriate service plan or another authorized endpoint. Version 2 makes brief foreground requests for six fixed places; it does not run a collection service or a background polling schedule.

`python3 scripts/record_forecasts.py --verify` checks the pinned records, their hashes and the matching Android assets. To inspect a new forecast without changing the release dataset, `--output-dir <new-directory>` records fresh responses in a new directory. Network responses change over time, so a fresh fetch is not expected to match the pinned bytes.
