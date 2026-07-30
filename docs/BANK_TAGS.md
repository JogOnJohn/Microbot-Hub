# Bank Tags Backup

Bank Tags plugin data exported from the laptop's Microbot profile
(`C:\Users\Billy\.runelite\microbot-profiles\default-1325219718795700.properties`) on 2026-07-30.

Contents: 5 tag tabs (`quester`, `agi`, `farming runs`, `tempoross`, `pc`), tab icons,
and 99 tagged items. Nothing sensitive — just item IDs and tag names.

## Import on another machine

1. **Close the Microbot client first.** It rewrites the profile file on exit and will
   clobber your edit if it is running.
2. Open the active profile file in `C:\Users\<user>\.runelite\microbot-profiles\` —
   it is the `default-*.properties` file with the most recent timestamp. (Microbot keeps
   its profiles here, not in the vanilla RuneLite `profiles2` folder.)
3. Delete any existing lines starting with `banktags.`.
4. Paste in every line from the block below and save the file.
5. Start the client. The tabs, icons, and item tags should all appear in the bank.

Notes:

- Keep the lines exactly as written — backslash escapes like
  `banktags.icon_farming\ runs` are part of the Java properties format.
- Alternative for a single tab: in the bank, right-click a tag tab and use
  **Export tag tab** (copies to clipboard), then right-click the **+** icon on the
  other machine and **Import tag tab**.

## Exported data

```properties
banktags.item_1271=farming runs
banktags.item_7409=farming runs
banktags.item_954=tempoross
banktags.item_952=farming runs
banktags.item_29893=farming runs
banktags.tagtabs=quester,agi,farming runs,tempoross,pc
banktags.item_995=quester,farming runs,pc
banktags.item_27281=farming runs
banktags.item_563=quester
banktags.item_560=quester
banktags.item_557=quester
banktags.item_555=quester
banktags.item_1706=tempoross,pc
banktags.item_21518=farming runs
banktags.item_12625=quester,agi,farming runs
banktags.item_12627=quester,farming runs
banktags.tab=
banktags.item_20235=quester,tempoross
banktags.item_10498=pc
banktags.item_13128=farming runs
banktags.layout__invsetup_c42e91627f37dee86ef45b3d916c9962=-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1
banktags.item_13123=farming runs
banktags.item_5998=farming runs
banktags.item_4251=farming runs
banktags.icon_te,p=952
banktags.item_6889=quester
banktags.icon_pc=11664
banktags.item_22997=farming runs
banktags.item_11858=quester,farming runs,agi,tempoross
banktags.item_11854=quester,farming runs,agi,tempoross
banktags.item_11856=quester,agi,farming runs,tempoross
banktags.item_11850=quester,farming runs,agi,tempoross
banktags.item_11852=quester,farming runs,agi,tempoross
banktags.item_11860=quester,agi,farming runs,tempoross
banktags.item_11849=agi
banktags.item_6016=farming runs
banktags.item_22975=quester,pc
banktags.icon_farming\ runs=6462
banktags.item_565=quester
banktags.item_9142=pc
banktags.item_22599=farming runs
banktags.item_12791=quester
banktags.item_13625=farming runs
banktags.item_13623=farming runs
banktags.item_13621=farming runs
banktags.item_13619=farming runs
banktags.item_13615=farming runs
banktags.item_9183=pc
banktags.item_2114=farming runs
banktags.item_22195=farming runs
banktags.item_22192=farming runs
banktags.useTabs=true
banktags.item_1065=pc
banktags.item_5416=farming runs
banktags.item_6731=quester
banktags.item_1099=pc
banktags.item_4587=pc
banktags.item_5497=farming runs
banktags.item_5499=farming runs
banktags.item_6328=pc
banktags.item_29273=farming runs
banktags.item_311=tempoross
banktags.item_1929=tempoross
banktags.item_11980=quester,tempoross
banktags.item_1917=quester
banktags.item_7218=agi,quester
banktags.item_22601=farming runs
banktags.item_11998=quester
banktags.rememberTab=true
banktags.item_2434=quester
banktags.item_12000=quester
banktags.item_12002=quester
banktags.item_22275=pc
banktags.item_3749=pc
banktags.item_11194=farming runs
banktags.item_11193=farming runs
banktags.item_11190=farming runs
banktags.item_11192=farming runs
banktags.item_11191=farming runs
banktags.item_5501=farming runs
banktags.item_5502=farming runs
banktags.item_1135=pc
banktags.item_5972=farming runs
banktags.item_5974=farming runs
banktags.item_5968=farming runs
banktags.item_24478=farming runs
banktags.item_5307=farming runs
banktags.item_5341=farming runs
banktags.item_5343=farming runs
banktags.item_5386=farming runs
banktags.item_5370=farming runs
banktags.item_5371=farming runs
banktags.item_5372=farming runs
banktags.item_5373=farming runs
banktags.item_5374=farming runs
banktags.item_4091=quester
banktags.item_4097=quester
banktags.item_4095=quester
banktags.item_4093=quester
banktags.item_4089=quester
banktags.item_5396=farming runs
banktags.removeTabSeparators=false
banktags.icon_agi=6514
banktags.item_8013=quester,agi,farming runs,pc
banktags.item_365=quester,agi
banktags.icon_tempoross=25602
banktags.item_329=quester
banktags.position=0
banktags.icon_quester=13068
banktags.item_21480=farming runs
banktags.item_1021=quester
banktags.item_2347=tempoross
banktags.preventTagTabDrags=false
```
