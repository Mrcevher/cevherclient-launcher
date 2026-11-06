package net.cevherclient.client.sao;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Menü ve modül ikonları: 12×12 birimlik kutuda vektör çizimler (beyaz çizilir, çizerken renklendirilir).
 * Dış görsel yoktur; hepsi bu dosyadaki koddan üretilir ve ekran çözünürlüğünde kenar yumuşatmalı çizilir.
 */
public enum SaoGlyph {
	// --- Ana menü ---
	MODULES(g -> {
		g.fill(new RoundRectangle2D.Float(1.2f, 1.2f, 4.3f, 4.3f, 1.2f, 1.2f));
		g.fill(new RoundRectangle2D.Float(6.5f, 1.2f, 4.3f, 4.3f, 1.2f, 1.2f));
		g.fill(new RoundRectangle2D.Float(1.2f, 6.5f, 4.3f, 4.3f, 1.2f, 1.2f));
		g.fill(new RoundRectangle2D.Float(6.5f, 6.5f, 4.3f, 4.3f, 1.2f, 1.2f));
	}),
	COSMETICS(g -> {
		g.fill(star(5.2f, 6.6f, 5.2f, 1.5f));
		g.fill(star(10.0f, 2.0f, 2.0f, 0.6f));
	}),
	EDITOR(g -> {
		g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(new Line2D.Float(6, 1.6f, 6, 10.4f));
		g.draw(new Line2D.Float(1.6f, 6, 10.4f, 6));
		g.fill(polygon(6, 0.2f, 7.8f, 2.4f, 4.2f, 2.4f));
		g.fill(polygon(6, 11.8f, 7.8f, 9.6f, 4.2f, 9.6f));
		g.fill(polygon(0.2f, 6, 2.4f, 4.2f, 2.4f, 7.8f));
		g.fill(polygon(11.8f, 6, 9.6f, 4.2f, 9.6f, 7.8f));
	}),
	CLOSE(g -> {
		g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(new Line2D.Float(2.4f, 2.4f, 9.6f, 9.6f));
		g.draw(new Line2D.Float(9.6f, 2.4f, 2.4f, 9.6f));
	}),
	SEARCH(g -> {
		g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(new Ellipse2D.Float(1.2f, 1.2f, 6.6f, 6.6f));
		g.draw(new Line2D.Float(6.8f, 6.8f, 10.6f, 10.6f));
	}),
	LOCK(g -> {
		g.fill(new RoundRectangle2D.Float(2.2f, 5.2f, 7.6f, 5.8f, 1.2f, 1.2f));
		g.setStroke(new BasicStroke(1.3f));
		g.draw(new java.awt.geom.Arc2D.Float(3.5f, 0.9f, 5f, 6f, 0, 180, java.awt.geom.Arc2D.OPEN));
	}),
	CROSS(g -> {
		g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(new Line2D.Float(2.6f, 2.6f, 9.4f, 9.4f));
		g.draw(new Line2D.Float(9.4f, 2.6f, 2.6f, 9.4f));
	}),

	// --- Modüller ---
	SWORD(g -> {
		g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(new Line2D.Float(10.4f, 1.0f, 4.6f, 6.8f));
		g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(new Line2D.Float(3.0f, 5.6f, 5.8f, 8.4f));
		g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(new Line2D.Float(4.6f, 7.0f, 2.4f, 9.2f));
		g.fill(new Ellipse2D.Float(0.8f, 9.0f, 2f, 2f));
	}),
	GAUGE(g -> {
		g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(new java.awt.geom.Arc2D.Float(1.2f, 1.6f, 9.6f, 9.6f, -30, 240, java.awt.geom.Arc2D.OPEN));
		g.draw(new Line2D.Float(6, 6.4f, 8.5f, 3.6f));
		g.fill(new Ellipse2D.Float(5f, 5.4f, 2f, 2f));
	}),
	SIGNAL(g -> {
		g.fill(new Rectangle2D.Float(1.2f, 7.8f, 1.8f, 3.2f));
		g.fill(new Rectangle2D.Float(4.0f, 5.6f, 1.8f, 5.4f));
		g.fill(new Rectangle2D.Float(6.8f, 3.4f, 1.8f, 7.6f));
		g.fill(new Rectangle2D.Float(9.6f, 1.0f, 1.8f, 10f));
	}),
	CURSOR(g -> g.fill(polygon(2.2f, 1f, 2.2f, 9.8f, 4.5f, 7.6f, 6.1f, 11f, 7.5f, 10.4f, 5.9f, 7.1f, 8.9f, 6.9f))),
	PIN(g -> {
		g.fill(new Ellipse2D.Float(2.4f, 1.0f, 7.2f, 7.2f));
		g.fill(polygon(2.9f, 6.2f, 9.1f, 6.2f, 6f, 11.4f));
		clear(g, () -> g.fill(new Ellipse2D.Float(4.7f, 3.3f, 2.6f, 2.6f)));
	}),
	CLOCK(g -> {
		g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(new Ellipse2D.Float(1.2f, 1.2f, 9.6f, 9.6f));
		g.draw(new Line2D.Float(6, 6, 6, 3.2f));
		g.draw(new Line2D.Float(6, 6, 8.2f, 7.3f));
	}),
	COMPASS(g -> {
		g.setStroke(new BasicStroke(1.1f));
		g.draw(new Ellipse2D.Float(0.9f, 0.9f, 10.2f, 10.2f));
		g.fill(polygon(6, 2.0f, 7.5f, 6, 4.5f, 6));
		g.fill(polygon(6, 10.0f, 4.5f, 6, 7.5f, 6));
	}),
	CHIP(g -> {
		g.fill(new RoundRectangle2D.Float(2.8f, 2.8f, 6.4f, 6.4f, 1f, 1f));
		for (float p : new float[] {3.8f, 5.6f, 7.4f}) {
			g.fill(new Rectangle2D.Float(p, 0.8f, 0.9f, 1.8f));
			g.fill(new Rectangle2D.Float(p, 9.4f, 0.9f, 1.8f));
			g.fill(new Rectangle2D.Float(0.8f, p, 1.8f, 0.9f));
			g.fill(new Rectangle2D.Float(9.4f, p, 1.8f, 0.9f));
		}
		clear(g, () -> g.fill(new Rectangle2D.Float(4.7f, 4.7f, 2.6f, 2.6f)));
	}),
	KEYS(g -> {
		g.fill(new RoundRectangle2D.Float(4.2f, 0.9f, 3.6f, 3.6f, 0.8f, 0.8f));
		g.fill(new RoundRectangle2D.Float(0.6f, 5.2f, 3.6f, 3.6f, 0.8f, 0.8f));
		g.fill(new RoundRectangle2D.Float(4.2f, 5.2f, 3.6f, 3.6f, 0.8f, 0.8f));
		g.fill(new RoundRectangle2D.Float(7.8f, 5.2f, 3.6f, 3.6f, 0.8f, 0.8f));
	}),
	ARMOR(g -> g.fill(polygon(3f, 1f, 4.6f, 1f, 6f, 2.3f, 7.4f, 1f, 9f, 1f, 11.6f, 3.7f, 9.8f, 5.5f,
		9f, 4.7f, 9f, 11f, 3f, 11f, 3f, 4.7f, 2.2f, 5.5f, 0.4f, 3.7f))),
	FLASK(g -> {
		g.fill(new Rectangle2D.Float(4.9f, 0.8f, 2.2f, 3.4f));
		g.fill(new Ellipse2D.Float(1.9f, 3.8f, 8.2f, 7.6f));
		clear(g, () -> {
			g.fill(new Rectangle2D.Float(3.6f, 4.0f, 4.8f, 0.7f));
			g.fill(new Ellipse2D.Float(4.2f, 7.0f, 1.6f, 1.6f));
		});
	}),
	CHEVRONS(g -> {
		g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		for (float x : new float[] {1.2f, 4.9f, 8.6f}) {
			Path2D path = new Path2D.Float();
			path.moveTo(x, 2f);
			path.lineTo(x + 2.4f, 6f);
			path.lineTo(x, 10f);
			g.draw(path);
		}
	}),
	MAGNIFIER(g -> {
		g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(new Ellipse2D.Float(1.2f, 1.2f, 6.6f, 6.6f));
		g.setStroke(new BasicStroke(1.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(new Line2D.Float(6.9f, 6.9f, 10.6f, 10.6f));
		g.setStroke(new BasicStroke(0.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(new Line2D.Float(4.5f, 2.8f, 4.5f, 6.2f));
		g.draw(new Line2D.Float(2.8f, 4.5f, 6.2f, 4.5f));
	}),
	HAT(g -> {
		g.fill(new RoundRectangle2D.Float(0.6f, 8.2f, 10.8f, 2.2f, 1f, 1f));
		g.fill(polygon(3.0f, 8.4f, 4.4f, 3.2f, 7.4f, 0.8f, 7.6f, 3.0f, 9.0f, 8.4f));
	}),
	HOURGLASS(g -> {
		g.fill(new RoundRectangle2D.Float(1.8f, 0.6f, 8.4f, 1.4f, 0.6f, 0.6f));
		g.fill(new RoundRectangle2D.Float(1.8f, 10f, 8.4f, 1.4f, 0.6f, 0.6f));
		g.fill(polygon(3f, 2f, 9f, 2f, 6.7f, 6f, 9f, 10f, 3f, 10f, 5.3f, 6f));
		clear(g, () -> g.fill(polygon(4.4f, 2.9f, 7.6f, 2.9f, 6f, 5f)));
	}),
	SPEED(g -> {
		g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(new Line2D.Float(2.2f, 3f, 7f, 3f));
		g.draw(new Line2D.Float(0.8f, 6f, 6f, 6f));
		g.draw(new Line2D.Float(2.2f, 9f, 7f, 9f));
		Path2D tip = new Path2D.Float();
		tip.moveTo(8f, 2.4f);
		tip.lineTo(11.2f, 6f);
		tip.lineTo(8f, 9.6f);
		g.draw(tip);
	}),
	TREE(g -> {
		g.fill(polygon(6f, 0.6f, 11f, 8.2f, 1f, 8.2f));
		g.fill(new Rectangle2D.Float(5f, 8f, 2f, 3.4f));
	}),
	SUN(g -> {
		g.fill(new Ellipse2D.Float(3.4f, 3.4f, 5.2f, 5.2f));
		g.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		for (int i = 0; i < 8; i++) {
			double a = Math.PI / 4 * i;
			g.draw(new Line2D.Double(6 + Math.cos(a) * 4.1, 6 + Math.sin(a) * 4.1, 6 + Math.cos(a) * 5.4, 6 + Math.sin(a) * 5.4));
		}
	}),
	MOON(g -> {
		g.fill(new Ellipse2D.Float(1.2f, 1.2f, 9.6f, 9.6f));
		clear(g, () -> g.fill(new Ellipse2D.Float(4.2f, -0.6f, 8.6f, 8.6f)));
	}),
	FOOD(g -> {
		g.fill(new Ellipse2D.Float(4.4f, 0.8f, 6.8f, 6.8f));
		g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(new Line2D.Float(5.6f, 6.4f, 2.6f, 9.4f));
		g.fill(new Ellipse2D.Float(0.6f, 8.6f, 2.2f, 2.2f));
		g.fill(new Ellipse2D.Float(1.8f, 9.8f, 2.2f, 2.2f));
	}),
	SERVER(g -> {
		g.fill(new RoundRectangle2D.Float(1f, 1.2f, 10f, 4.2f, 1f, 1f));
		g.fill(new RoundRectangle2D.Float(1f, 6.6f, 10f, 4.2f, 1f, 1f));
		clear(g, () -> {
			g.fill(new Ellipse2D.Float(2.4f, 2.6f, 1.4f, 1.4f));
			g.fill(new Ellipse2D.Float(2.4f, 8f, 1.4f, 1.4f));
			g.fill(new Rectangle2D.Float(5.4f, 2.9f, 4f, 0.8f));
			g.fill(new Rectangle2D.Float(5.4f, 8.3f, 4f, 0.8f));
		});
	}),
	BOLT(g -> g.fill(polygon(7.2f, 0.4f, 2.4f, 6.9f, 5.7f, 6.9f, 4.6f, 11.6f, 9.6f, 4.8f, 6.3f, 4.8f))),
	RULER(g -> {
		g.fill(new RoundRectangle2D.Float(0.6f, 3.8f, 10.8f, 4.4f, 0.8f, 0.8f));
		clear(g, () -> {
			for (float x = 2.2f; x < 10.5f; x += 1.9f) {
				g.fill(new Rectangle2D.Float(x, 3.8f, 0.7f, x % 3.8f < 1.9f ? 2.6f : 1.6f));
			}
		});
	}),
	BOW(g -> {
		g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(new java.awt.geom.Arc2D.Float(1.4f, 0.8f, 8.4f, 10.4f, 90, 180, java.awt.geom.Arc2D.OPEN));
		g.setStroke(new BasicStroke(0.7f));
		g.draw(new Line2D.Float(5.6f, 0.8f, 5.6f, 11.2f));
		g.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(new Line2D.Float(2.6f, 6f, 10.4f, 6f));
		g.fill(polygon(11.6f, 6f, 9.4f, 4.6f, 9.4f, 7.4f));
	}),
	TOTEM(g -> {
		g.fill(new RoundRectangle2D.Float(3.2f, 0.8f, 5.6f, 10.4f, 2f, 2f));
		g.fill(new RoundRectangle2D.Float(0.8f, 5.2f, 10.4f, 1.8f, 0.8f, 0.8f));
		clear(g, () -> {
			g.fill(new Rectangle2D.Float(4.3f, 2.6f, 1.2f, 1.3f));
			g.fill(new Rectangle2D.Float(6.5f, 2.6f, 1.2f, 1.3f));
			g.fill(new Rectangle2D.Float(4.6f, 8.4f, 2.8f, 0.8f));
		});
	}),
	CART(g -> {
		g.fill(polygon(0.6f, 2.8f, 11.4f, 2.8f, 9.8f, 7.8f, 2.2f, 7.8f));
		g.fill(new Ellipse2D.Float(2.2f, 8.2f, 2.6f, 2.6f));
		g.fill(new Ellipse2D.Float(7.2f, 8.2f, 2.6f, 2.6f));
	}),
	LIST(g -> {
		for (float y : new float[] {1.6f, 5.2f, 8.8f}) {
			g.fill(new Ellipse2D.Float(0.8f, y, 1.8f, 1.8f));
			g.fill(new RoundRectangle2D.Float(3.8f, y + 0.2f, y == 5.2f ? 5.6f : 7.4f, 1.4f, 0.6f, 0.6f));
		}
	}),
	PACK(g -> {
		g.fill(polygon(6f, 0.6f, 11.2f, 3.4f, 6f, 6.2f, 0.8f, 3.4f));
		g.fill(polygon(0.8f, 4.6f, 5.4f, 7.1f, 5.4f, 11.6f, 0.8f, 9f));
		g.fill(polygon(11.2f, 4.6f, 6.6f, 7.1f, 6.6f, 11.6f, 11.2f, 9f));
	}),
	MESSAGE_BAR(g -> {
		g.fill(new RoundRectangle2D.Float(0.6f, 3.2f, 10.8f, 5.6f, 1.6f, 1.6f));
		clear(g, () -> {
			g.fill(new Rectangle2D.Float(2.2f, 5.4f, 4.8f, 1.2f));
			g.fill(new Rectangle2D.Float(7.8f, 5.4f, 2f, 1.2f));
		});
	}),
	SKULL(g -> {
		g.fill(new Ellipse2D.Float(1.2f, 0.6f, 9.6f, 8.8f));
		g.fill(new RoundRectangle2D.Float(3.4f, 7.2f, 5.2f, 4.2f, 1f, 1f));
		clear(g, () -> {
			g.fill(new Ellipse2D.Float(3f, 4f, 2.4f, 2.4f));
			g.fill(new Ellipse2D.Float(6.6f, 4f, 2.4f, 2.4f));
			g.fill(new Rectangle2D.Float(5.6f, 9.2f, 0.8f, 2.2f));
		});
	}),
	TABLE(g -> {
		g.fill(new RoundRectangle2D.Float(1f, 0.8f, 10f, 10.4f, 1f, 1f));
		clear(g, () -> {
			for (float y : new float[] {3.6f, 5.8f, 8f}) {
				g.fill(new Rectangle2D.Float(2.2f, y, 5f, 1.2f));
				g.fill(new Rectangle2D.Float(8.2f, y, 1.6f, 1.2f));
			}
		});
	}),
	PERSON(g -> {
		g.fill(new Ellipse2D.Float(3.8f, 0.6f, 4.4f, 4.4f));
		g.fill(new RoundRectangle2D.Float(2.2f, 5.6f, 7.6f, 6f, 3f, 3f));
	}),
	EYE(g -> {
		g.fill(new Ellipse2D.Float(0.4f, 2.8f, 11.2f, 6.4f));
		clear(g, () -> g.fill(new Ellipse2D.Float(3.6f, 3.6f, 4.8f, 4.8f)));
		g.fill(new Ellipse2D.Float(4.9f, 4.9f, 2.2f, 2.2f));
	}),
	BULB(g -> {
		g.fill(new Ellipse2D.Float(2.4f, 0.6f, 7.2f, 7.2f));
		g.fill(new Rectangle2D.Float(4.2f, 6.6f, 3.6f, 2.4f));
		g.fill(new RoundRectangle2D.Float(4.4f, 9.4f, 3.2f, 1.8f, 0.8f, 0.8f));
	}),
	CAMERA(g -> {
		g.fill(new RoundRectangle2D.Float(0.6f, 3f, 10.8f, 7.6f, 1.6f, 1.6f));
		g.fill(new Rectangle2D.Float(3.6f, 1.4f, 3.6f, 2f));
		clear(g, () -> g.fill(new Ellipse2D.Float(3.6f, 4.4f, 4.8f, 4.8f)));
		g.fill(new Ellipse2D.Float(4.8f, 5.6f, 2.4f, 2.4f));
	}),
	CROSSHAIR(g -> {
		g.setStroke(new BasicStroke(1.1f));
		g.draw(new Ellipse2D.Float(2.2f, 2.2f, 7.6f, 7.6f));
		g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(new Line2D.Float(6f, 0.4f, 6f, 3.8f));
		g.draw(new Line2D.Float(6f, 8.2f, 6f, 11.6f));
		g.draw(new Line2D.Float(0.4f, 6f, 3.8f, 6f));
		g.draw(new Line2D.Float(8.2f, 6f, 11.6f, 6f));
	}),
	CUBE(g -> {
		g.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(polygon(6f, 0.8f, 11f, 3.4f, 11f, 8.6f, 6f, 11.2f, 1f, 8.6f, 1f, 3.4f));
		g.draw(new Line2D.Float(1f, 3.4f, 6f, 6f));
		g.draw(new Line2D.Float(11f, 3.4f, 6f, 6f));
		g.draw(new Line2D.Float(6f, 6f, 6f, 11.2f));
	}),
	HITBOX(g -> {
		g.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {1.8f, 1.2f}, 0f));
		g.draw(new Rectangle2D.Float(2.2f, 0.8f, 7.6f, 10.4f));
		g.fill(new Ellipse2D.Float(4.6f, 2f, 2.8f, 2.8f));
		g.fill(new RoundRectangle2D.Float(4.2f, 5.4f, 3.6f, 4.6f, 1.4f, 1.4f));
	}),
	RING(g -> {
		g.setStroke(new BasicStroke(1.2f));
		g.draw(new Ellipse2D.Float(0.8f, 6.2f, 10.4f, 4.4f));
		g.fill(new Ellipse2D.Float(4.6f, 0.6f, 2.8f, 2.8f));
		g.fill(new RoundRectangle2D.Float(4.4f, 3.6f, 3.2f, 4.8f, 1.4f, 1.4f));
	}),
	HEART(g -> {
		Path2D heart = new Path2D.Float();
		heart.moveTo(6f, 11f);
		heart.curveTo(-1.5f, 5.6f, 2.2f, -0.6f, 6f, 3.2f);
		heart.curveTo(9.8f, -0.6f, 13.5f, 5.6f, 6f, 11f);
		heart.closePath();
		g.fill(heart);
	}),
	DROP(g -> {
		g.fill(polygon(6f, 0.8f, 9.4f, 4.2f, 6f, 7.6f, 2.6f, 4.2f));
		g.fill(new RoundRectangle2D.Float(0.6f, 9.6f, 10.8f, 1.6f, 0.8f, 0.8f));
	}),
	CRYSTAL(g -> {
		g.fill(polygon(6f, 0.4f, 10.4f, 6f, 6f, 11.6f, 1.6f, 6f));
		clear(g, () -> g.fill(polygon(6f, 3.4f, 8f, 6f, 6f, 8.6f, 4f, 6f)));
	}),
	NO_ARMOR(g -> {
		g.fill(polygon(3f, 1f, 4.6f, 1f, 6f, 2.3f, 7.4f, 1f, 9f, 1f, 11.6f, 3.7f, 9.8f, 5.5f,
			9f, 4.7f, 9f, 11f, 3f, 11f, 3f, 4.7f, 2.2f, 5.5f, 0.4f, 3.7f));
		clear(g, () -> {
			g.setStroke(new BasicStroke(2.6f));
			g.draw(new Line2D.Float(0f, 0f, 12f, 12f));
		});
		g.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(new Line2D.Float(0.8f, 0.8f, 11.2f, 11.2f));
	}),
	RESIZE(g -> {
		g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(new Line2D.Float(1.6f, 10.4f, 10.4f, 1.6f));
		g.fill(polygon(11.4f, 0.6f, 11.4f, 5f, 7f, 0.6f));
		g.fill(polygon(0.6f, 11.4f, 0.6f, 7f, 5f, 11.4f));
	}),
	NO_EYE(g -> {
		g.fill(new Ellipse2D.Float(0.4f, 2.8f, 11.2f, 6.4f));
		clear(g, () -> {
			g.fill(new Ellipse2D.Float(3.6f, 3.6f, 4.8f, 4.8f));
			g.setStroke(new BasicStroke(2.6f));
			g.draw(new Line2D.Float(0f, 0f, 12f, 12f));
		});
		g.fill(new Ellipse2D.Float(4.9f, 4.9f, 2.2f, 2.2f));
		g.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(new Line2D.Float(0.8f, 0.8f, 11.2f, 11.2f));
	}),
	SPARKS(g -> {
		g.fill(star(4.4f, 4.6f, 3.8f, 1.1f));
		g.fill(star(9.2f, 8.8f, 2.6f, 0.8f));
		g.fill(new Ellipse2D.Float(8.4f, 1.4f, 1.8f, 1.8f));
		g.fill(new Ellipse2D.Float(2f, 9.4f, 1.6f, 1.6f));
	}),
	CHAT(g -> {
		g.fill(new RoundRectangle2D.Float(0.6f, 1f, 10.8f, 7.6f, 2.4f, 2.4f));
		g.fill(polygon(2.6f, 7.8f, 5.6f, 7.8f, 2.4f, 11.2f));
		clear(g, () -> {
			g.fill(new Ellipse2D.Float(2.8f, 4f, 1.4f, 1.4f));
			g.fill(new Ellipse2D.Float(5.3f, 4f, 1.4f, 1.4f));
			g.fill(new Ellipse2D.Float(7.8f, 4f, 1.4f, 1.4f));
		});
	}),
	TROPHY(g -> {
		g.fill(polygon(2.6f, 0.8f, 9.4f, 0.8f, 9f, 4.4f, 6f, 7.2f, 3f, 4.4f));
		g.setStroke(new BasicStroke(1f));
		g.draw(new java.awt.geom.Arc2D.Float(0.6f, 1.4f, 3.4f, 3.6f, 90, 180, java.awt.geom.Arc2D.OPEN));
		g.draw(new java.awt.geom.Arc2D.Float(8f, 1.4f, 3.4f, 3.6f, -90, 180, java.awt.geom.Arc2D.OPEN));
		g.fill(new Rectangle2D.Float(5.3f, 6.8f, 1.4f, 2.6f));
		g.fill(new RoundRectangle2D.Float(3f, 9.4f, 6f, 1.8f, 0.6f, 0.6f));
	}),
	WINGS(g -> {
		g.fill(polygon(5.4f, 3.2f, 3.4f, 1.2f, 0.4f, 1.6f, 0.8f, 5.2f, 1.8f, 8.6f, 4f, 10.4f, 5.4f, 7f));
		g.fill(polygon(6.6f, 3.2f, 8.6f, 1.2f, 11.6f, 1.6f, 11.2f, 5.2f, 10.2f, 8.6f, 8f, 10.4f, 6.6f, 7f));
	}),
	CAPE(g -> {
		g.fill(new RoundRectangle2D.Float(2.6f, 0.8f, 6.8f, 1.6f, 0.8f, 0.8f));
		g.fill(polygon(3f, 2.2f, 9f, 2.2f, 10.2f, 11.2f, 6f, 10f, 1.8f, 11.2f));
	});

	/** İkon kutusunun kenarı (birim). */
	public static final float SIZE = 12f;

	private final Consumer<Graphics2D> painter;

	SaoGlyph(Consumer<Graphics2D> painter) {
		this.painter = painter;
	}

	/** (x, y) GUI konumuna size×size GUI boyutunda çizer. Sao.alpha uygulanır. */
	public void draw(GuiGraphics graphics, float x, float y, float size, int color) {
		SaoRaster.draw(graphics, "glyph_" + name() + "_" + size, x, y, size, size, Sao.fade(color), g -> {
			g.scale(size / SIZE, size / SIZE);
			g.setColor(Color.WHITE);
			painter.accept(g);
		});
	}

	/** Merkezi (cx, cy) olan ikon. */
	public void drawCentered(GuiGraphics graphics, float cx, float cy, float size, int color) {
		draw(graphics, cx - size / 2f, cy - size / 2f, size, color);
	}

	private static Path2D polygon(float... xy) {
		Path2D path = new Path2D.Float();
		path.moveTo(xy[0], xy[1]);
		for (int i = 2; i < xy.length; i += 2) {
			path.lineTo(xy[i], xy[i + 1]);
		}
		path.closePath();
		return path;
	}

	/** Dört köşeli parıltı yıldızı. */
	private static Path2D star(float cx, float cy, float outer, float inner) {
		Path2D path = new Path2D.Float();
		for (int i = 0; i < 8; i++) {
			double angle = Math.PI / 4 * i - Math.PI / 2;
			float r = i % 2 == 0 ? outer : inner;
			float x = cx + (float) Math.cos(angle) * r;
			float y = cy + (float) Math.sin(angle) * r;
			if (i == 0) {
				path.moveTo(x, y);
			} else {
				path.lineTo(x, y);
			}
		}
		path.closePath();
		return path;
	}

	/** Çizilen şekilden boşluk açar (ör. iğnenin deliği). */
	private static void clear(Graphics2D g, Runnable shapes) {
		Composite previous = g.getComposite();
		g.setComposite(AlphaComposite.Clear);
		shapes.run();
		g.setComposite(previous);
	}
}
