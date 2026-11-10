import { Request, Response } from 'express';
import { db } from '../database/db';

export const getAdStrategy = (req: Request, res: Response) => {
  const { placement_ids, country_code } = req.body;
  const targetIds = placement_ids || ['trans_complete_interstitial', 'main_bottom_banner'];

  const placementsMap: Record<string, any> = {};

  db.ad_placements
    .filter(p => p.is_active && targetIds.includes(p.placement_id))
    .forEach(p => {
      placementsMap[p.placement_id] = {
        format: p.placement_type,
        waterfall: p.mediation_waterfall,
        auto_refresh_sec: p.placement_type === 'banner' ? 30 : 0
      };
    });

  return res.json({
    code: 0,
    message: 'success',
    data: {
      credentials: db.ad_global_keys,
      placements: placementsMap,
      anti_fraud: {
        click_limit_per_minute: 5,
        verify_token: 'af_token_9182390123'
      }
    }
  });
};

export const trackImpression = (req: Request, res: Response) => {
  const { placement_id, network, device_uuid } = req.body;
  return res.json({ code: 0, message: 'Impression recorded' });
};

export const trackClick = (req: Request, res: Response) => {
  const { placement_id, network, device_uuid } = req.body;
  return res.json({ code: 0, message: 'Click recorded' });
};

export const trackCPI = (req: Request, res: Response) => {
  return res.json({ code: 0, message: 'Attribution recorded' });
};

