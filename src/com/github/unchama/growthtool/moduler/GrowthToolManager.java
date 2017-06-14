package com.github.unchama.growthtool.moduler;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.github.unchama.gigantic.Gigantic;
import com.github.unchama.growthtool.GrowthTool.GrowthToolType;
import com.github.unchama.growthtool.moduler.message.GrwMessage;
import com.github.unchama.growthtool.moduler.message.GrwTips;
import com.github.unchama.growthtool.moduler.status.GrwEnchants;
import com.github.unchama.growthtool.moduler.status.GrwStatus;
import com.github.unchama.growthtool.moduler.tool.GrwDefine;
import com.github.unchama.growthtool.moduler.tool.GrwTool;
import com.github.unchama.task.GrowthToolTaskRunnable;
import com.github.unchama.util.Util;
import com.github.unchama.yml.DebugManager;
import com.github.unchama.yml.DebugManager.DebugEnum;
import com.github.unchama.yml.GrowthToolDataManager;

public abstract class GrowthToolManager {
	DebugManager debug = Gigantic.yml.getManager(DebugManager.class);

	// 成長ツール名称
	private String name;
	// ドロップバランス
	private int dropBalanceRate;
	// 識別用固有メッセージ
	private List<String> identLore;
	// ステータス一覧
	private GrwStatus status;
	// エンチャント一覧
	private GrwEnchants enchant;
	// Tips一覧
	private GrwTips tipsMsg;
	// 整地時のメッセージリスト
	private GrwMessage onBlockBreakMsg;
	// 命名時のメッセージリスト
	private GrwMessage onRenameItemMsg;
	// 討伐時のメッセージリスト
	private GrwMessage onMonsterKillMsg;
	// 被ダメージ時のメッセージリスト
	private GrwMessage onGetDamageMsg;
	// 破損警告時のメッセージリスト
	private GrwMessage onWarnItemMsg;
	// 破損時のメッセージリスト
	private GrwMessage onBreakItemMsg;
	// プレイヤーログアウト時のメッセージリスト
	private GrwMessage onPlayerQuitMsg;

	/**
	 * 装備中の一致する成長ツールを取得する。装備中ではない場合はnullが返却される。
	 *
	 * @param player
	 * @return
	 */
	protected abstract GrwTool getTool(Player player);

	// getToolでitemを取得したらこれを呼び出す
	// 所有者一致確認は行わない
	protected GrwTool getTool(ItemStack item) {
		if (!item.hasItemMeta()) {
			return null;
		}
		ItemMeta itemmeta = item.getItemMeta();
		if (!itemmeta.hasLore()) {
			return null;
		}
		List<String> itemlore = itemmeta.getLore();
		// identを含むか判定
		for (String identLine : identLore) {
			if (!itemlore.contains(GrwDefine.IDENTHEAD + identLine)) {
				return null;
			}
		}
		return new GrwTool(item, identLore, status, enchant);
	}

	/**
	 * 装備中の一致する成長ツールを置換する。
	 *
	 * @param player
	 * @return
	 */
	protected abstract void setTool(Player player, GrwTool newtool);
	// TODO setTool不要説

	public GrowthToolManager(GrowthToolType type) {
		name = type.name();
		// ymlからの読み込み
		GrowthToolDataManager configmanager = Gigantic.yml.getManager(GrowthToolDataManager.class);
		dropBalanceRate = configmanager.getDropBalance(type);
		identLore = configmanager.getIdent(type);
		// Status
		final Map<Integer, Material> base = configmanager.getBaseItem(type);
		final List<Integer> nextExp = configmanager.getExp(type);
		final List<List<String>> custom1 = configmanager.getStringListList(type, "custom1");
		final List<List<String>> custom2 = configmanager.getStringListList(type, "custom2");
		final Integer unbreakable = configmanager.getUnbreakableLv(type);
		status = new GrwStatus(base, nextExp, custom1, custom2, unbreakable);
		// Enchants
		enchant = configmanager.getEnchantments(type);
		// Tips
		List<String> original = configmanager.getStringList(type, "tipsmsg");
		List<String> wiki = loadTips(configmanager.getWikiUrl(type));
		tipsMsg = new GrwTips(name, original, wiki, custom1);
		// メッセージリスト
		onBlockBreakMsg = new GrwMessage(name, configmanager.getStringList(type, "breakmsg"));
		onRenameItemMsg = new GrwMessage(name, configmanager.getStringList(type, "renamemsg"));
		onMonsterKillMsg = new GrwMessage(name, configmanager.getStringList(type, "killmsg"));
		onGetDamageMsg = new GrwMessage(name, configmanager.getStringList(type, "damagemsg"));
		onWarnItemMsg = new GrwMessage(name, configmanager.getStringList(type, "warnmsg"));
		onBreakItemMsg = new GrwMessage(name, configmanager.getStringList(type, "destroymsg"));
		onPlayerQuitMsg = new GrwMessage(name, configmanager.getStringList(type, "quitmsg"));
	}

	/**
	 * アイテムLv1で生成し配布する。
	 *
	 * @param player
	 * @return
	 */
	public boolean giveDefault(Player player) {
		Util.giveItem(player, (ItemStack) create(player), false);
		return true;
	}

	/**
	 * アイテムの表示名を変更する。
	 *
	 * @param player
	 * @param name
	 * @return
	 */
	public boolean rename(Player player, String name) {
		GrwTool tool = getTool(player);
		if (tool != null) {
			String trim = trimInputText(name);
			if (trim.isEmpty()) {
				tool.setName(this.name);
				player.sendMessage(this.name + "の名前を初期化しました。");
			} else {
				tool.setName(trim);
				player.sendMessage(this.name + "の名前を" + trim + "に変更しました。");
			}
			setTool(player, tool);
			// TODO taskで一元管理して名前を付けて喋らせる
			GrowthToolTaskRunnable.talk(player, onRenameItemMsg.talk(tool, player, null), false);
			return true;
		}
		player.sendMessage(this.name + "を装備していません。");
		return false;
	}

	/**
	 * ドロップバランスを取得する。
	 *
	 * @return
	 */
	public int getDropBalance() {
		return dropBalanceRate;
	}

	/**
	 * Tips出力時の出力候補メッセージ選定。装備中かつメッセージが設定されている場合は1つ選択して返却。該当しない場合はnullを返却する。
	 *
	 * @param player
	 * @return
	 */
	public String getTipsMsg(Player player) {
		// 装備中の場合
		GrwTool tool = getTool(player);
		if (tool != null) {
			return tipsMsg.getTips(tool);
		}
		return null;
	}

	/**
	 * ブロック破壊時の出力候補メッセージ選定。 装備中かつメッセージが設定されている場合は1つ選択して返却。該当しない場合はnullを返却する。
	 *
	 * @param player
	 * @return
	 */
	public String getBlockBreakMsg(Player player) {
		GrwTool tool = getTool(player);
		if (tool != null) {
			if (tool.addExp()) {
				setTool(player, tool);
			}
			return onBlockBreakMsg.talk(tool, player, null);
		}
		return null;
	}

	/**
	 * モンスター討伐時の出力候補メッセージ選定。 装備中かつメッセージが設定されている場合は1つ選択して返却。該当しない場合はnullを返却する。
	 *
	 * @param player
	 * @param monster
	 * @return
	 */
	public String getMonsterKillMsg(Player player, Monster monster) {
		GrwTool tool = getTool(player);
		if (tool != null) {
			return onMonsterKillMsg.talk(tool, player, monster);
		}
		return null;
	}

	/**
	 * 被ダメージ時の出力候補メッセージ選定。 装備中かつメッセージが設定されている場合は1つ選択して返却。該当しない場合はnullを返却する。
	 *
	 * @param player
	 * @param monster
	 * @return
	 */
	public String getDamage(Player player, Monster monster) {
		GrwTool tool = getTool(player);
		if (tool != null) {
			if (tool.isWarn()) {
				return onWarnItemMsg.talk(tool, player, monster);
			} else {
				return onGetDamageMsg.talk(tool, player, monster);
			}
		}
		return null;
	}

	/**
	 * 破損時の出力候補メッセージ選定。 破損アイテムと一致かつメッセージが設定されている場合は1つ選択して返却。該当しない場合はnullを返却する。
	 *
	 * @param player
	 * @param tool
	 * @return
	 */
	public String getBreakMsg(Player player, ItemStack broke) {
		GrwTool tool = getTool(broke);
		if (tool != null) {
			return onBreakItemMsg.talk(tool, player, null);
		}
		return null;
	}

	/**
	 * プレイヤーログアウト時の出力候補メッセージ選定。 装備中かつメッセージが設定されている場合は1つ選択して返却。該当しない場合はnullを返却する。
	 *
	 * @param player
	 * @return
	 */
	public String getPlayerQuitMsg(Player player) {
		GrwTool tool = getTool(player);
		if (tool != null) {
			return onPlayerQuitMsg.talk(getTool(player), player, null);
		}
		return null;
	}

	/**
	 * webからのTipsリスト読み込み処理
	 */
	private List<String> loadTips(String wikiurl) {
		List<String> tips = new ArrayList<String>();
		try {
			// HTTP通信でJSONデータを取得
			URL url = new URL(wikiurl);
			URLConnection urlCon = url.openConnection();
			// 403回避のためユーザーエージェントを登録
			urlCon.setRequestProperty("User-Agent", "GrowthTool");
			InputStream in = urlCon.getInputStream();
			BufferedReader reader = new BufferedReader(new InputStreamReader(in, "EUC-JP"));
			String line;
			// Tips先頭まで読み込み
			while ((line = reader.readLine()) != null) {
				if (line.contains("<ul id=\"content_block_2\" class=\"list-1\">")) {
					break;
				}
			}
			// Tipsを読み込み
			while ((line = reader.readLine()) != null) {
				if (line.contains("</ul>")) {
					break;
				} else {
					tips.add(line.replace("<li> ", "").replace("</li>", ""));
				}
			}
			reader.close();
			in.close();
		} catch (Exception e) {
			debug.warning(DebugEnum.GROWTHTOOL, "tips読み込み失敗: " + wikiurl);
			tips.clear();
		}
		return tips;
	}

	private GrwTool create(Player player) {
		return new GrwTool(player, name, identLore, status, enchant);
	}

	/**
	 * msg内の<name>に対応する呼び名を設定する。
	 *
	 * @param player
	 * @param called
	 * @return
	 */
	public boolean setPlayerCalled(Player player, String called) {
		GrwTool tool = getTool(player);
		if (tool != null) {
			String trim = trimInputText(called);
			if (trim.isEmpty()) {
				tool.setCall("");
				player.sendMessage(name + "からの呼び名を初期化しました。");
			} else {
				tool.setCall(trim);
				player.sendMessage(name + "からの呼び名を" + trim + "に変更しました。");
			}
			setTool(player, tool);
			return true;
		}
		player.sendMessage(name + "からの呼び名を変更出来ませんでした。");
		return false;
	}

	/**
	 * 入力文字列から除外文字を除去する。Color Codeを除去し、10文字を上限として返却する。
	 *
	 * @param text
	 * @return
	 */
	private String trimInputText(String text) {
		ChatColor.stripColor(text);
		text = text.replace("<", "").replace(">", "");
		if (text.length() > 10) {
			text = text.substring(0, 10);
		}
		return text;
	}

	public String getMessage(Event event) {
		if (event instanceof BlockBreakEvent) {
			// player
		} else if (event instanceof EntityDamageByEntityEvent) {
			// player, monster ... warning cast error entity->player, monster
			// if durability warning, out the warning msg
		} else if (event instanceof EntityDeathEvent) {
			// player, monster ... warning cast error entity->player, monster
		} else if (event instanceof PlayerItemBreakEvent) {
			// player ... with SystemMsg + SystemSound
			// is broken this tool?
		} else if (event instanceof PlayerQuitEvent) {
			// player
		}
		return "";
	}
}