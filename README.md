# GTRadio
An open-source, custom radio player for Android designed to produce similar functional results to the radios found in the Grand Theft Auto series of games.

GTRadio was originally a fun project for myself, but I consider it a collaborative, community project; so, feel free to jump in and contribute!

**IMPORTANT**<br>
**GTRadio is not affiliated with Rockstar Games and does not come with any game audio files, and we cannot provide those files. However...**
You can extract game audio files, as long as you own the games, via excellent community tools [in this section](#useful-audio-extraction-tools).

## Installing
### Play Store
GTRadio is currently in the process of being added to the Play Store. Once that info is available, it will be posted here.

### Sideloading
GTRadio can be sideloaded via the released APK found in [Releases](https://github.com/hellcat707hp/GTRadio/releases).

The process for sideloading apps is pretty simple and a more comprehensive guide can be found elsewhere, but the main points:
1. Ensure your device is set to allow apps installed from unknown sources.
2. Download the APK file and move it to your device's storage.
3. Find the APK on your device using a file manager, and choose to open/install the APK.

**A note on sideloading and Android Auto** <br>
Android Auto only trusts/shows apps installed via the Play Store, meaning normal sideloading will result in GTRadio not being available in Android Auto. 
There is a workaround using adb to install the APK that marks the app as installed by the Play Store. 
More info on that process can be found [here](https://medium.com/@pixplicity/setting-the-install-vendor-of-an-app-7d7deacb01ee).


## Audio File Setup
- GTRadio is designed to work with files local to the device, organized in a specific folder structure and file naming scheme.
- GTRadio can work with any files organized and setup in the structure specified, but it was designed with GTA game audio files in mind.
- We are aware that extracting and organizing your own game audio files to spec can be a tedious task, and some day we may create a tool for it;
but, for now, there are plenty of great tools out there to extract GTA game music and allow for you to organize it manually.
- Before diving too deep into this, you might want to explore the example station groups and stations found [here](https://github.com/hellcat707hp/GTRadio-SampleData).

### Folder & File Structure
Folder structure and naming of your files is of the utmost importance with GTRadio.

It is highly recommended that you add `.nomedia` file into the root folder to prevent media players from seeing these files as part of your music.

There are 3 types of of radio stations that GTRadio supports, each with their own requirements for folder/file structure.
- [Gen1](#gen1)
  - GTA III and Vice City
- [Gen2](#gen2)
  - GTA San Andreas
- [Gen3](#gen3)
  - GTA IV

#### Station Groups
Stations should be grouped together based on their type (grouped by video game essentially), and the type must be defined in a `stationGroupInfo.json` file formatted as follows, 
where X is a number 1-3 and "Group Name" is the display name for the station group.
```
{
  "generation": X,
  "name": "Group Name"
}
```

The top level folder contains the station groups, where the station folders live. Each station group must contain a `stationGroupInfo.json` file and a logo image file.
```
📁 Main Folder
├──📁 {station_group_gen1}
│   ├── stationGroupInfo.json
│   ├── logo.{any_image_format}
│   └── ...
├──📁 {station_group_gen2}
│   ├── stationGroupInfo.json
│   ├── logo.{any_image_format}
│   └── ...
├──📁 {station_group_gen3}
│   ├── stationGroupInfo.json
│   ├── logo.{any_image_format}
│   └── ...
├─── .nomdeia   
└
```

#### Stations
Stations will be defined as folders inside a station group.

> Note: Any files with `.{audio}` can be any Android-supported audio file extension, and files with `.{image}` can be any Android-supported image file extension.

##### Gen1 
These are basic stations with a single file on loop.

**Example Games:** GTA III and GTA Vice City

**For GTA III,** radio files are already .wav files and can be found in `{game_install_directory}/audio`. <br>
**For Vice City,** radio files are in .adf files and can be found in `{game_install_directory}/Audio`, 
but they must be converted to MP3s using a tool like [adf2mp3](https://github.com/Codeuctivity/adf2mp3)

Structure for Gen1 station groups:
```
📁 Main Folder
├──📁 {station_group_gen1}
│   ├──📁 {Station_Name_1}
│   │   ├── {any_file_name}.{audio}
│   │   └── logo.{image}
│   ├── ... (repeat more station folders)
│   ├── stationGroupInfo.json
│   └── logo.{image}
```

##### Gen2
More advanced dynamic stations with various DJ chatter, announcer spots, song intros and outros, and ads.

**Example Games:** GTA San Andreas

**For San Andreas,** radio files are stored in archive files found in `{game_install_directory}/audio/streams`. <br>
The individual files for a given SA station can be extracted using a tool like [radio-free-san-andreas](https://github.com/creideiki/radio-free-san-andreas).

Structure for Gen2 station groups:

> Yes, these get a bit complex. We have future plans to follow a similar file naming scheme to Gen3 to alleviate the folder mess;
>  however, the current structure is a result of the [tools](#useful-audio-extraction-tools) we had originally to extract music from Gen2 games.
```
📁 Main Folder
├──📁 {station_group_gen2}
│   ├──📁 Adverts
│   ├──📁 {Station_Name_1}
│   │   ├──📁 Announcer
│   │   │   ├── {any_file_name}.{audio}
│   │   │   └── ...
│   │   ├──📁 DJ Chatter
│   │   │   ├──📁 Evening
│   │   │   │   ├── {any_file_name}.{audio}
│   │   │   │   └── ...
│   │   │   ├──📁 Morning
│   │   │   │   ├── {any_file_name}.{audio}
│   │   │   │   └── ...
│   │   │   ├──📁 Night
│   │   │   │   ├── {any_file_name}.{audio}
│   │   │   │   └── ...
│   │   │   ├──📁 Other
│   │   │   │   ├── {any_file_name}.{audio}
│   │   │   │   └── ...
│   │   │   ├──📁 Weather
│   │   │   │   ├──📁 Fog
│   │   │   │   │   ├── {any_file_name}.{audio}
│   │   │   │   │   └── ...
│   │   │   │   ├──📁 Rain
│   │   │   │   │   ├── {any_file_name}.{audio}
│   │   │   │   │   └── ...
│   │   │   │   ├──📁 Storm
│   │   │   │   │   ├── {any_file_name}.{audio}
│   │   │   │   │   └── ...
│   │   │   └   └──📁 Sun
│   │   │           ├── {any_file_name}.{audio}
│   │   │           └── ...
│   │   │   
│   │   ├──📁 Songs
│   │   │   ├──📁 Song 1
│   │   │   │   ├── {song_name1} (Intro).{audio}
│   │   │   │   ├── {song_name1}.{audio}
│   │   │   │   ├── {song_name1} (Outro).{audio}
│   │   │   │   ├── {song_name1} (Outro DJ {X}).{audio}
│   │   │   │   ├── {song_name1} (Intro DJ {X}).{audio}
│   │   │   │   └── ... (any further DJ intro and outro files)
│   │   │   └── ...
│   │   └── logo.{image}
│   ├── stationGroupInfo.json
│   └── logo.{image}
```

##### Gen3
Similar to Gen2 stations with differences in file structure and now includes weather and news reports. They can also be the Gen1 style where its a single looping file.

**Example Games:** GTA 4 

**For GTA 4,** radio files are stored in .rpf archive files found in `{game_install_directory}/pc/audio/sfx`. <br>
These files can be opened and extracted using a tool like [OpenIV](https://openiv.com/).<br>
Also note that GTA 4 has had songs removed in newer releases due to license expirations. There are ways to restore these old files, assuming you own the games.
Looking for "GTA 4 downgrade tools" should get you there.

Structure for Gen3 station groups:
```
📁 Main Folder
├──📁 {station_group_gen1}
│   ├──📁 {Station_Name_1_Music_Station}
│   │   ├──📁 Songs
│   │   │   ├── {song_name1}.{audio}
│   │   │   ├── {song_name1}_{X}.{audio}
│   │   │   └── ... (repeat song files with {X} intros)
│   │   │
│   │   ├── MORNING_{X}.{audio}
│   │   ├── AFTERNOON_{X}.{audio}
│   │   ├── EVENING_{X}.{audio}
│   │   ├── NIGHT_{X}.{audio}
│   │   ├── ID_{X}.{audio}
│   │   ├── SOLO_{X}.{audio}
│   │   ├── TO_AD_{X}.{audio}
│   │   ├── TO_NEWS_{X}.{audio}
│   │   ├── TO_WEATHER_{X}.{audio}
│   │   ├── INTRO_{X}.{audio}
│   │   ├── OUTRO_{X}.{audio}
│   │   ├── ... (repeat any number of the above files)
│   │   └── logo.{any_image_format}
│   ├──📁 {Station_Name_2_TalkShow_Station}
│   │   ├──📁 Talk Shows
│   │   │   ├── {show_name}.{audio}
│   │   │   └── ... (repeat show files)
│   │   ├── ID_{X}.{audio}
│   │   └── logo.{any_image_format}
│   ├──📁 {Station_Name_3_SingleFile_Station}
│   │   ├── {any_file_name}.{audio}
│   │   └── logo.{image}
│   ├── ... (repeat more station folders)
│   ├── stationGroupInfo.json
│   └── logo.{image}
```

###### Gen3 File Name Meanings
Above you will see various file name formats. Here are the meanings of those:
- `MORNING_{X}`, `AFTERNOON_{X}`, `EVENING_{X}`, `NIGHT_{X}`
  - DJ chatter specifically mentioning the time
- `ID_{X}`
  - Announcer spot that usually indentifies the station.
- `SOLO_{X}`
  - A longer DJ spot that usually identifies the station.
- `TO_AD_{X}`, `TO_NEWS_{X}`, `TO_WEATHER_{X}`
  - A DJ transition to ads, news, or weather reports.
- `INTRO_{x}`, `OUTRO_{x}`
  - Generic song intro and outros (like Independence FM)


### Useful Audio Extraction Tools
Here are some useful tools you might need/want in order to extract and organize your game music.

Our team does not own or endorse these tools, they're just options.

- [adf2mp3](https://github.com/Codeuctivity/adf2mp3)
  - Used to convert .adf files from Vice City to standard mp3 files. 
  - There are other tools that do this, and the code is pretty simple.
- [radio-free-san-andreas](https://github.com/creideiki/radio-free-san-andreas)
  - Used to extract San Andreas music. Currently our Gen2 folder structure is based partly on what it outputs.
- [OpenIV](https://openiv.com/)
  - Used to extract GTA IV music. This is what our file naming is based off for Gen3 stations.